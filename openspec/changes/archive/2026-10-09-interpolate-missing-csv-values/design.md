## Context

`CsvFile.open` loads every CSV and fixed-width file. It runs on the shared executor (from `Loader.openCsvFile` and
`Loader.loadFrom`), never on the FX thread. Current sequence:

```
parser.parse(file)                      empty cell -> null, number -> Integer/Double, other text -> String
MissingValues.fillGeoDataPositions      lat/lon only: spline over the whole file, ignores lines
create marks (FoundPlace aux elements)
reorderLines() + mergeSinglePointLines  line ids become final and contiguous here
setUnsaved(false)
```

`LinearInterpolator.interpolate(x, y)` already does what one line needs: it repairs the axis with
`makeMonotonic(x)` (interpolates missing x, drops out-of-order x, falls back to the row index when x is entirely
missing), fills interior gaps of `y` linearly over `x`, and fills the head and tail of `y` with its first and last
present value. NaN marks a missing value in both arrays.

## Goals / Non-Goals

**Goals:**
- Reuse `LinearInterpolator`; add only the per-line, per-column driver around it.
- Keep the fill a single step of the load sequence, with no effect on the edit paths.

**Non-Goals:**
- Generalizing the fill for `SgyFileWithMeta` files or exposing it as a series filter.
- Changing `fillGeoDataPositions`.

## Decisions

**1. Run after the line structure is final.** Call the fill in `CsvFile.open` after `reorderLines()` and
`mergeSinglePointLines()`, before `setUnsaved(false)`. Earlier, line ids are raw values from the file and a
single-point line has not been merged yet, so segments would differ from what the user sees.
Alternative: fill inside the parser. Rejected: the parser does not know the final line structure.

**2. Segments are runs of equal line ids.** After `reorderLines()` every row has a sequential, contiguous line id,
so a line is a maximal run of rows with the same `getLine()` value. The driver walks the rows once and handles each
run. This avoids depending on the cached line ranges of `SgyFile`, which are not built yet during `open`.

**3. Column selection by `Column.isDisplay()`, minus the Line semantic.** The parser sets `display` to "not a template
meta field and either declared in `data-values` or containing numbers", marks are hidden and lines are forced
visible. Excluding the Line semantic explicitly keeps the segmentation key out of the fill, even though it never has
gaps after `reorderLines()`. Position, date, time, timestamp and trace number are meta fields, so they are excluded
without extra checks.
Alternative: only template `data-values`. Rejected: undeclared numeric columns would keep their gaps.

**4. Axis from the row timestamp.** For each line, build `x` from `GeoData.getTimestamp()` (NaN when null), repair it
with `makeMonotonic` (missing times are interpolated, no times at all fall back to row order), then spread runs of
equal times evenly up to the next distinct time. Coarse time columns (e.g. whole seconds with 10 Hz data) repeat
timestamps; without spreading, an empty row sharing the left anchor's time would take that anchor's value. The last run
has no next time and continues with the previous run's per-row step; a line where every row has the same time uses a
step of one, i.e. row order. Rows before the first timed row are extrapolated back from it with the first step, rather
than tied to it, so spreading never moves a row away from its own timestamp. The result is strictly monotonic, so the interpolator's own `makeMonotonic` call leaves it
unchanged and the same `x` is reused for every column of the line.
Alternative: use row order for a whole line as soon as any timestamp repeats. Rejected: a single repeat would discard
the time axis.

**5. Write back only into empty cells.** For each selected column, `y` holds the numeric value, or NaN for both null
and String cells. After interpolation the driver writes a `Double` only where the original cell was null and the
result is not NaN. String cells are therefore neither anchors nor targets, and a column with no numbers in a line stays
empty there.
Values are written as `Double` regardless of the column's other values (accepted in the proposal); `Text.formatNumber`
writes whole doubles without a fractional part.

**6. Code location.** Add a `MissingValues` method that fills the values of a `List<GeoData>`, next to
`fillGeoDataPositions`, and call it from `CsvFile.open`. The fill is not reported: a load warning would open a dialog
for almost every file, and `fillGeoDataPositions` is silent too. A per-column check skips lines where a column has no
empty cells.
Alternative: a load warning when anything was filled. Rejected for the reasons above.

**7. No undo, unsaved or cache handling.** The fill is part of building the initial state: no snapshot is taken,
`setUnsaved(false)` stays after it, and no `tracesChanged()` or events are needed because no chart, cache or listener
sees the file before `open` returns. `Loader.loadFrom` (reload from disk) gets the fill too, because it calls `open`;
its existing `model.reload` refreshes the chart and caches.

**8. Allocation.** Per line, allocate one `x` array and one `y` array reused across columns. Work is linear in
rows times selected columns, run once per load. `Interpolator.linear()` and `Interpolator.spline()` return shared
instances; both implementations hold no state, so concurrent loads on the executor can share them and callers no longer
pass an interpolator around.

## Risks / Trade-offs

- [Integer or code columns (satellites, fix quality) get fractional values] → Accepted for now; rounding is a non-goal
  and can be revisited per column type later.
- [Long decimals such as `5.666666666666667` in saved files] → Accepted; same formatting as other computed values.
- [Scope is tied to the parser's `display` rule; a later change to that rule changes which columns are filled] → The
  spec states the column set in user terms, so such a change has to update the spec too.
- [Out-of-order timestamps] → `makeMonotonic` repairs the axis only; data values are never reordered.
- [Spread positions inside a repeated timestamp are estimates] → They assume even sampling within the time step, which
  matches how coarse time columns arise.
- [Latitude/longitude stay spline-filled across lines while other columns are filled per line] → Accepted; changing
  the position fill is a non-goal.
