## Context

`GriddingTool` loads its inputs in `loadPreferences()`: the parameters of the file's grid (`GridLayer.getResult`),
otherwise `GriddingSettings.loadParams`, which falls back to 0.1 / 1.0 m when the template has no saved values.
`applyFilter()` calls `savePreferences()`, so any palette, range or post-processing change saves the current parameters
too; `submitAction` saves them on Apply. Inputs are limited by `MAX_CELL_SIZE` and `MAX_BLANKING_DISTANCE` (100 m).

`PrincipalComponents.findComponents(List<Point2D>)` computes the centred covariance of positions through a Jama matrix;
`LineSchema` uses it per line.

The fork JeffRoth/GeoHammer (`SurveyGeometryService`) estimates the line spacing from the Line column: line
orientations, primary line family, median perpendicular offset per line, median gap between sorted offsets; cell size =
spacing / 4, blanking = spacing x 2. We keep neither its reliance on line metadata nor its ratios (see "Gridding
tool").

## Goals / Non-Goals

**Goals:**
- A geometry-only line spacing estimate fast enough to run on every file selection (see the survey-geometry spec).
- Two candidate estimators compared on the same data, then one of them deleted (see "Comparison of the estimators").
- Reuse `PrincipalComponents` through a small generic addition instead of a local copy.

**Non-Goals:**
- Caching estimates per file: an estimate is cheap enough to recompute, so no cache invalidation is needed.
- Detecting the line structure for editing (splitting a file into lines); only the spacing is exposed.
- A pluggable estimator abstraction: one estimator is kept.

## Decisions

### SurveyService and the track in local meters

New `@Service` `com.ugcs.geohammer.service.survey.SurveyService` with
`OptionalDouble estimateLineSpacing(SgyFile file)`. It reads latitude/longitude by semantic (as
`GriddingService.getDataPoints` does), skips rows without a position, and projects the rest in row order to local
meters with an equirectangular projection around the first position (`x = dLon * cos(lat0) * R`, `y = dLat * R`,
`R = SphericalMercator.R`). The track is two `double[]` arrays, x and y. Equirectangular is exact enough over a
survey area and costs one multiplication per point. `SphericalMercator.project` is not used: Web Mercator stretches
distances by `1 / cos(latitude)`, so it would need a scale correction, and it allocates a `Point2D` per row.

The estimate itself is the package-private `LineSpacing.estimate(double[] x, double[] y)` returning `OptionalDouble`.
It rotates the two arrays in place, so no second pair of arrays is allocated; `SurveyService` creates them for this
call only. `StretchStatistics` is a private record inside `LineSpacing` with the statistics of one stretch of the
track (`compute(x, y, IndexRange)`) and the constants and check that make a stretch a line.

Alternatives: a method on `GriddingService` (gridding is only one consumer; MCP and future QC tools need the same
analysis); estimating from `DataPoint`s (they carry a series value and are built per series).

### Line reconstruction (PCA)

```
track (x, y)
  -> dominant direction: PCA of steps weighted by length          O(n)
  -> rotate in place: x along lines, y across lines               O(n)
  -> stretches between the reversals of x                         O(n)
  -> statistics of every stretch; long straight ones are lines    O(n), L lines
     (xMin, xMax, yMean, yDeviation, yFitDeviation)
  -> sort by yMean; per line half of the distance to the
     second neighbor above it                                     O(L log L)
  -> spacing = median
```

- **Direction.** Steps are unit vectors weighted by length, summed into an uncentred 2x2 second-moment matrix
  `sum(len * u * u^T)`; its principal eigenvector is the line direction, sign-invariant. Weighting by length keeps GPS
  jitter and turns from dominating; weighting by length squared (what the existing covariance of step vectors gives)
  lets single long jumps over removed turns dominate. There is no check of the eigenvalue ratio: position noise lowers
  it without tilting the direction, so it would reject valid noisy surveys; tracks without lines are rejected by the
  line check below.
- **Minimum line length.** One scale, a tenth of the track extent along x (`MIN_LINE_LENGTH_RATIO`), is used three
  ways: a reversal of x shorter than it does not end a stretch, a shorter stretch is not a line, and two lines are
  neighbors only if they overlap along x by more than it. The extent (`estimateLineLength`) is the range of evenly
  spread values with the deviation of x (`sqrt(12 * variance)`, as the variance of values spread evenly over a range
  L is `L^2 / 12`), which a transit to the survey area stretches much less than `max - min`. Halving the reversal
  threshold changed no result on the sample files, so a separate constant is not kept.
- **Lines.** `detectStretches` cuts the track where x turns back by more than the threshold;
  `StretchStatistics.compute` takes the points of a stretch without a tenth of its length at each end
  (`MARGIN_RATIO`, the turns); `isLine` accepts the stretch when it is longer than the minimum line length and the
  `yDeviation` of its points (from the mean y) is no more than 5% of its length; tie lines, transits and loops fail
  it, and a stretch without points fails it as its statistics are NaN. A margin of 5% was tried and rejected: turns
  leak into the lines, wobbly lines fail the check, and the estimate of two sample files went from 26 to 35 m and
  from 5.5 to 5.0 m. `yFitDeviation` is the deviation from the straight line fitted to the points, i.e. without the
  slope of the line in the frame.
- **Neighbors.** Lines are sorted by `yMean`. The neighbor of a line (`nextNeighbor`) is the nearest line above it
  that overlaps it along x and is farther than the sum of both `yFitDeviation`: lines are bands with the fit deviation
  as the half-width, and overlapping bands follow the same path (a re-flown line, a file merged twice). Parts of one
  line and lines of another block do not overlap along x, so they are not compared. `yDeviation` cannot replace
  `yFitDeviation` here: it includes the slope of the line in the frame, which on long lines exceeds the spacing at a
  direction error of about a degree; on the sample files it changes 24 of 177 estimates, most of them to twice the
  spacing.
- **Spacing.** The spacing of a line is half of the distance to the neighbor of its neighbor, i.e. the mean of two
  consecutive gaps (the single gap for the line before the last one; the last line has none). The estimate is the
  median over the lines; one spacing, so two lines, is enough. The mean of two gaps gives the average spacing where
  lines are shifted in pairs: a sideways offset of the recorded position relative to the direction of travel
  alternates between lines flown in opposite directions (alternating 0.55 / 1.46 m gaps in one sample give 1.0 m,
  where the median of single gaps gave 1.34 m). Median, not mean, over the lines: a skipped line does not shift it.
  A queue that removes both lines of a matched pair was considered and rejected: it measures every other gap only,
  which gives 0.6 or 1.4 m on such a survey.

### PrincipalComponents: components of vectors

Add `Components2 findOffsetComponents(double[] x, double[] y)`: the principal axes of the steps of a path given by its
points, where a step counts by its length. It takes the steps between consecutive points, sums `d * d^T / |d|` into
an uncentred 2x2 matrix, normalizes it by the total length (so the component lengths are the shares of the path
length along them) and solves it in closed form. Steps and their weights are computed inside, so the caller passes
the track as it is and no step or weight arrays are allocated. No `Point2D` or Jama matrix per step: a million steps
would otherwise allocate about 100 MB. The name differs from `findComponents(List<Point2D>)`, which centres positions
by their mean; that method and `LineSchema` stay unchanged.

Alternatives: pass scaled step vectors to the existing method, rejected for the centring (the mean step is near zero
only for back-and-forth surveys), the length-squared weighting, and the allocations; a general weighted variant
`findComponents(x, y, weights)` and a variant over step arrays `(dx, dy)`, both implemented first and dropped as the
only caller always passed the steps of a track weighted by 1 / length.

### Comparison of the estimators

Two estimators were implemented and compared; line reconstruction was chosen by the user and the other one deleted.

The rejected **parallel pass** estimator resampled the track, built a `KdTree` over its vertices and, for 2000 sampled
vertices, took the nearest vertex that is far away along the track and runs in parallel; the spacing was the median
perpendicular distance. It needed a filtered nearest neighbor search in `KdTree`, removed with it.

| | Line reconstruction | Parallel pass |
|---|---|---|
| Synthetic surveys of the spec | exact | exact, but -9% with noise of 1/10 spacing |
| Real surveys measured on track plots (1.0, 5.9, 24.5 m) | 1.00, 6.01, 24.96 | 0.88, 4.23, 16.92 |
| 38 sample files that are not line surveys | no estimate | arbitrary values |
| Spirals, strongly curved lines | no estimate | estimated |
| 1M points | 10 ms, 23 MB | 14 ms, 6 MB |
| 1M points on a single line | 9 ms | 61 ms, 64 MB |

Parallel pass measures the nearest pass, i.e. the smaller of the two gaps around a line, so it is biased low whenever
lines wobble; this is structural and cannot be tuned away. It also answers for tracks that are not surveys. Line
reconstruction checks its assumptions and returns no estimate when they do not hold.

Tests: only `PrincipalComponentsTest` is kept. The comparison was made with tests of synthetic tracks (a track
generator with a known spacing), a performance test of a 1M-row file, and a report of estimate, time and allocated
bytes over a folder of real surveys. They were removed after the comparison: their expectations depend on the tuned
thresholds of the estimator and on local data.

### Gridding tool

- **Row and button.** The inputs row (`HBox` of two `InputWithTopLabel`) gets an `Auto` button aligned to the bottom
  (`Pos.BOTTOM_LEFT`), styled with a new `.button.flat` rule in `components.css` mirroring `.toggle-button.flat`.
- **Estimate.** `GriddingParams.fromLineSpacing(double lineSpacing)` is a static factory of the record, which owns
  the ratios and limits: cell = 0.2 x spacing, blanking = 1 x spacing, rounded to 2 decimals with the new
  `Numbers.round`, clamped to [0.01, 100]. The limits move from `GriddingTool` to `GriddingParams` constants, with
  `GriddingParams.DEFAULT` (0.1 / 1.0) as the fallback of the template defaults. Callers get the spacing from
  `SurveyService`, so `GriddingService` needs no dependency on it and the MCP tool reports the spacing it estimated
  from.
- **Ratios.** There is no formal standard for the cell size; the common guidance is a quarter to a fifth of the line
  spacing (Oasis montaj defaults to a quarter of the data separation). A fifth is taken: it is the finest value in
  that range and keeps round values for round spacings (1 m gives 0.2, 5 m gives 1), where a quarter gives 0.25 and
  0.13. A tenth looked smoothest but is finer than the convention and makes the grid 4 times larger (about
  100 x lines^2 cells for a square survey). For the blanking distance the guidance is only "just above the distance
  to interpolate over": half a spacing fills between adjacent lines, one spacing also bridges a single skipped line
  and extends the grid one spacing beyond the outer lines. 0.75 was rejected: the blanking is applied as a whole
  number of cells, so it reaches 0.6 of the spacing and leaves stripes of empty cells in the wide gaps of lines
  shifted in pairs.
- **Threads.** Estimates run on the shared executor via a `SinglePendingExecutor` in `GriddingTool` (last wins). The
  result is applied on the FX thread only if `selectedFile` is still the estimated file. The Auto button is not
  disabled meanwhile: an estimate takes milliseconds and repeated clicks are coalesced. Not registered in
  `TaskService`.
- **Auto pressed.** On success the inputs are set like typed text; the existing listeners validate and show the
  "recalculate" warning. On no estimate the inputs keep their values and the tool shows the warning "No survey lines
  detected" with auto-hide. An exception of the estimate is logged and treated as no estimate.
- **Template defaults.** `GriddingSettings.loadParams` returns the saved parameters or null instead of falling back
  to 0.1 / 1.0. In `loadPreferences()`: grid params, else saved params, else start an estimate with `init` set; on
  success the estimate fills the inputs and is saved with `saveParams`, on no estimate `GriddingParams.DEFAULT` fills
  the inputs and nothing is saved, so the next file of the template is estimated again. The inputs are not cleared
  while the estimate runs: it takes milliseconds. The estimate is started only for files the tool is shown for, not
  for GPR files.
- **Saving.** `applyFilter()` saves only the filter (`saveFilter`); `savePreferences()` keeps saving both and is still
  called by `submitAction` on Apply / Apply to all.

No file data changes: no snapshot, undo, `setUnsaved` or cache invalidation. No new events; `FileSelectedEvent` and
`GridUpdatedEvent` handling stays as is.

### Warning banner of the tools

`FilterToolView` gets a warning label as the first child of the tool container, hidden by default, with
`showWarning(String message, boolean autoHide)` and `hideWarning()`. With auto-hide a `PauseTransition` hides the
label after `WARNING_DURATION` (5 s); a repeated call restarts it. The style class `.warning-banner` in
`components.css` uses the theme variables `-color-warning` for the background and `-color-accent-text` for the text,
with rounded corners. `GriddingTool` shows both of its warnings in it: "Grid needs to be recalculated" (until hidden)
and "No survey lines detected" (auto-hide); its own warning label is removed.

`GriddingTool.onChange` shows the recalculation warning on every `traceCut` / `traceValues` event without checking
that the selected file has a grid; the next change of the inputs hides it again. When a file is opened this shows the
banner for a moment. The warning has to be shown only for a file with a grid (see the gridding spec); the same
sequence existed with the old label, where it was less noticeable.

Alternatives for the "no survey lines" feedback: a dialog (too distracting, rejected by the user), the status bar
(easy to miss, away from the tool), the existing `Toast` popup at the inputs (tried, looked out of place), disabling
"Auto" (needs an estimate on every selection, and a disabled button cannot explain itself).

### MCP tool

`EstimateGriddingParams` (`estimate_gridding_params`), read-only (`modifiesFiles()` false), optional `file` argument
like other tools. Calls `SurveyService.estimateLineSpacing` and `GriddingParams.fromLineSpacing`, returns
`cell_size`, `blanking_distance` and the unrounded `line_spacing`; throws `IllegalArgumentException` when no lines
are detected. The description states the ratios, the rounding and the limits, and points agents to
`{{run_gridding}}`. Registered in `McpTools`.

## Risks / Trade-offs

- [Thresholds fail on real tracks] → checked on 177 sample files: regular surveys of 0.5 and 1 m are estimated
  within 2% of the spacing measured on track plots; surveys with few or wobbly lines (6 m and 25 m) within about 10%.
- [No automated test of the estimator] → only `PrincipalComponentsTest` is kept; a change of the thresholds has to be
  checked on sample surveys by hand.
- [No estimate for curved lines and spirals] → accepted; "Auto" then warns and keeps the inputs.
- [Two lines are enough for an estimate] → tracks that are not surveys but have two parallel stretches get a value
  (an out-and-back wedge and a criss-cross flight among the samples); accepted, so that surveys of two lines work.
- [A single path flown several times with GPS drift between the passes looks like a very narrow survey] → gives a
  small spacing (0.3 - 0.5 m in two sample files); such a file has nothing to grid.
- [Existing users already have 0.1 / 1.0 saved by a palette change] → they keep them; "Auto" is one click. Treating
  those exact values as unset would be a guess about user intent.
- [Blanking clamped to 100 m for line spacing above 100 m] → gaps between lines remain visible; the cap is kept as a
  non-goal.
- [Samples along a line more than two line spacings apart] → the blanking of one spacing leaves holes along the
  lines; the user raises the blanking distance.
- [Estimating on every selection of a template without saved parameters] → only until the first estimate is saved;
  last-wins executor drops stale runs.
- [The warning banner shifts the tool content when it appears and hides] → one row, at the top of the tool.
