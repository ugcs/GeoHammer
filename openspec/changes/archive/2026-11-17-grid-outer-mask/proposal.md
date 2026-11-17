## Why

A grid is shown wherever a cell is within the blanking distance of a data point, so it spills outside the survey by
the whole blanking distance: beyond the outer lines, beyond the line ends and into every area without data. With the
estimated parameters this is a full line spacing of extrapolated values around the measured data, which draws
anomalies where nothing was measured (#1088).

## What Changes

- The grid is shown inside the outline of the gridded data points and one cell around it, instead of everywhere
  within the blanking distance of a data point.
- The blanking distance decides which gaps are filled: data up to twice the blanking distance apart is joined into
  one outline, so the space between survey lines is filled as before.
- Outside the outer lines and beyond the line ends the grid ends one cell from the data, for any survey orientation.
- A gap wider than twice the blanking distance is left empty up to one cell from the data around it, whether it is a
  missing line, a notch of a concave survey or an area without data inside the survey. It no longer has a rim of the
  blanking distance.
- Data farther apart than twice the blanking distance is outlined separately: separate survey blocks are not joined,
  and a single line or point is shown one cell around its cells.
- The grid array covers the data and two cells around it instead of the data and the blanking distance around it,
  so it is smaller and gridding is faster. Interpolated values can differ marginally from the current ones.
- The description of the blanking distance parameter of the MCP gridding tool states the new meaning.

## Non-goals

- Changing the interpolation itself (splines parameters, cell medians).
- A setting or parameter for the outer margin or for the outline shape.
- Renaming the blanking distance or changing its estimate, limits or saved settings.
- Reusing or changing the concave hull of the quality checks: it is too slow on concave surveys for gridding (see
  design.md).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gridding`: a new requirement on which cells of a grid are shown around the gridded data.

## Impact

- `service/gridding`: new classes `GridMask` and `CellIndex` (moved out of the service); in
  `GriddingService.runGridding` the loop that fills the visibility mask is replaced by one call and the envelope
  padding is reduced. `GriddingParams`,
  `GriddingResult`, settings and the grid layer are not changed.
- `mcp/tool/RunGridding`: parameter description text only.
- Existing grids are not affected until they are recalculated; grids are not persisted.
- `spec/overview.md`: the glossary entry of the blanking distance.
