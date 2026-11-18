## Context

See proposal.md for motivation. `GriddingService.runGridding` works in these steps:

1. Envelope of the data points, padded by the blanking distance plus one cell.
2. Data points binned into cells (`cellValues`), cell median minus the global median.
3. Splines interpolation over the whole array.
4. Visibility mask `visiblePoints`: a square of `blankingRadius = (int) (blankingDistance / cellSize)` cells around
   every data cell.
5. Cells outside the mask become `NaN`; the result carries the array and the padded envelope.

The spill around the data comes from step 4. Step 1 only makes room for it.

Gridding runs on the shared virtual-thread executor and checks the interrupt flag between steps. No events, file
data, undo or caches are involved: the change is inside the computation of a `GriddingResult`.

## Goals / Non-Goals

**Goals:**

- Only the envelope padding (step 1) and the visibility mask (step 4) change; the other steps, signatures and data
  structures stay as they are.
- The mask is linear in the number of grid cells for any radius.
- The grid array is not larger than the mask needs.

**Non-Goals:**

- Renaming the blanking distance parameter.
- Keeping interpolated values bit-identical: a smaller array moves the edge of the splines solution.

## Decisions

### 1. The visibility mask is built by `GridMask`

The loop of step 4 is replaced by `new GridMask(blankingRadius, margin).create(gridWidth, gridHeight,
cellValues.keySet())`. `GridMask` is a package-level class in `service/gridding` that holds the blanking radius and
the margin in cells; the margin of one cell is a constant of `GriddingService`. It knows nothing about splines or
`GriddingParams`, so it is tested on its own with hand-built cells.

`CellIndex` moves from a private record of `GriddingService` to a package-level record in its own file, so that
`GridMask` and its tests can use it.

- Alternative: keep the square fill and clip it in a second step. Implemented first and replaced: without a rim in
  enclosed gaps (decision 3) the fill is fully overridden by the outline, and it costs the square of the radius per
  data cell.
- Alternative: private methods in `GriddingService`. Rejected: the mask could then be tested only through files and
  full gridding runs.

### 2. Mask: closing of the data cells and a margin

1. **Closing** of the data cells by the blanking radius: spread the data cells by the radius, then shrink the result
   back by the same radius (square neighbourhood). Gaps up to twice the radius between data are filled; nothing
   remains outside the outer lines and line ends or in wider gaps.
2. **Margin**: the result is spread by the margin.

Spreading and shrinking are separable passes along each axis that track the distance to the last matching cell and
write in place through a line buffer.

The closing needs the cells within the radius of the data, which the grid no longer has (decision 5). `GridMask`
works on its own array padded by the blanking radius on every side and returns the part that matches the grid, so
the grid border never affects the outline.

The closing never extends beyond the square of the radius around data, so no cell farther than the blanking distance
from data is shown, as before.

- Alternative: a circle of the radius instead of a square, so the widest gap that is filled does not depend on the
  survey angle (a square reaches 1.41 radii along the diagonals). Implemented with a distance pass and rejected after
  testing on real surveys: the rounded outline drops single cells on the edges and is wavy between the line ends.

### 3. No rim in gaps enclosed by data

An area without data inside the survey that is wider than twice the blanking distance is treated like a gap that is
open to the outside: the grid ends one cell from the data around it.

- The rim is extrapolation inside the survey just as it is outside it.
- A gap looks the same whether or not it happens to be closed off: a wide gap between two lines does not change when
  a tie line crosses its ends.
- Alternative: mark the empty cells connected to the grid border (flood fill) and keep the rim of the blanking
  distance in the enclosed ones. Implemented and removed for the reasons above; it also needs the square fill, a
  third cell state and a stack.

### 4. Closing radius is the blanking radius

`runGridding` passes its `blankingRadius`. Gaps up to twice the blanking distance were filled before and are filled
now. With the estimated parameters (blanking distance equal to the line spacing) adjacent lines are joined and the
outline runs across the line ends. No setting is added.

### 5. Envelope padding is the margin

The grid is never shown farther than the margin from the data, so the envelope is padded by the margin plus one cell
(for the rounding of the grid size to whole cells) instead of the blanking distance plus one cell. The array and the
splines solve get smaller.

The splines solution near the edge depends on where the array ends, so values change slightly: in the test survey
(60 x 56 m, values within 85..115) the sampled cells moved by up to 0.014. `GriddingServiceTest` keeps the values
captured with the old padding and compares with a tolerance of 0.05.

### 6. Rejected outline algorithms

- `service/quality/ConcaveHull` on the data cells, buffered and rasterised: it digs into a concave part one cell at
  a time and queries all points near a long edge at every step. Measured for an L-shaped survey with lines every 5
  cells: 3 s on a 500 x 500 grid, 20 s on 1000 x 1000, 158 s on 2000 x 2000.
- First and last data cell of every row and column, joined over the radius: the outer edge is tight at any angle,
  but a gap between lines is clipped when the lines run along the grid axes and keeps its rim when the survey is
  rotated.
- A rim per data cell chosen by the presence of data on each side along the grid axes: on a slanted line the
  neighbours of a cell on its own line count as data on both sides, so rotated surveys keep the full spill.
- Showing a cell only when the data in its window surrounds it: gaps are filled only up to the window radius, wider
  gaps show a strip in the middle with hidden cells next to the lines, and the exact test costs the window area per
  cell.

## Risks / Trade-offs

- [The blanking distance no longer means "show everything within this distance of data": gaps wider than twice the
  blanking distance, enclosed or not, lose their rim, and a single line is one cell wide] → specified; the parameter
  description of the MCP tool and the glossary are updated.
- [Lines spaced wider than twice the blanking distance show one cell around each line instead of stripes of the
  blanking distance] → specified; the same rule as for a single line.
- [A gap just under twice the blanking distance is filled and one just over is left empty] → accepted; the same
  threshold decided before whether the two rims met.
- [With a square the widest gap that is filled grows from twice the blanking distance along the grid axes to 2.8
  times at 45 degrees] → accepted; the square fill that the mask replaces had the same dependence, and the circle
  looked worse on the edges.
- [Interpolated values and the shading at the grid edge shift with the smaller array] → measured as marginal in the
  test survey; not checked on real surveys with strong anomalies at the edge.
- [`GridMask` allocates a `boolean` array larger than the grid by the blanking radius on every side] → a quarter of
  a `float` grid of that size, released when the mask is returned.
