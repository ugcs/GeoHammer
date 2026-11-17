## 1. Blanking mask

- [x] 1.1 Add `GridMask` to `service/gridding`: created with a blanking radius and a margin in cells; from the grid
  size and the data cells (`CellIndex`) it returns the data cells closed by the radius with the margin around them,
  computed on an array padded by the radius; verify with unit tests on a single straight line of cells and on one
  and two cells (cells with their eight neighbours)
- [x] 1.2 Verify the outline shapes with unit tests: a rotated block of parallel lines (ends one cell outside the
  outer lines and line ends), an L-shaped block (notch is outside), a block with a hole wider than two radii (hole is
  outside), two lines two radii apart (gap is inside) and wider apart (gap is outside), two separate blocks (space
  between them is outside); every data cell is in the mask
- [x] 1.3 Add a test that builds the mask for a large rotated survey and a large L-shaped survey and checks the time
  against a bound; verify it passes

## 2. Gridding

- [x] 2.1 In `GriddingService.runGridding`, replace the loop that fills `visiblePoints` with a `GridMask` of the
  blanking radius and a margin of one cell, created for the keys of `cellValues`; move `CellIndex` to its own file;
  verify existing `GriddingServiceTest` tests pass
- [x] 2.2 Pad the envelope by the margin plus one cell instead of the blanking distance plus one cell; verify the
  gridding tests pass
- [x] 2.3 Add `GriddingServiceTest` cases: the grid ends one cell outside the outer lines, an enclosed gap wider than
  twice the blanking distance is empty beyond one cell, and the cells shown keep the values captured with the old
  padding within a tolerance; verify the tests pass

## 3. Descriptions and specs

- [x] 3.1 Update the `blanking_distance` description in `mcp/tool/RunGridding` with its new meaning (gaps up to
  twice the distance are filled, elsewhere the grid ends one cell from the data); verify the text reads correctly in
  the tool schema
- [x] 3.2 Update the Grid entry of the glossary in `spec/overview.md` to state the new meaning of the blanking
  distance; verify the entry matches the delta spec

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings; verify the build
  and all tests pass
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
