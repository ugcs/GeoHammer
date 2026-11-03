## Why

The low-pass filter produces wrong results (#1035). A filter length of 1 turns the whole `_LPF` series into NaN, and
every line of 3–4 measurements is filtered with length 1 whatever length the user enters. The end of every line is
filtered against samples taken from the wrong place, so a ramp 1190..1199 ends at about 1197.5 instead of 1199. Two
more defects distort every result: even filter lengths shift the series by about 0.3 measurements along the line, and
each line is rescaled by the ratio of root mean squares, which spreads errors at the line end over the whole line and
inflates series that vary around zero.

## What Changes

- A filter length of 1 returns the values unchanged, as a length of 0 does today.
- Each line end is extended by mirroring the line around its end value (point reflection), so a linear trend continues
  through the end and is kept exactly; previously the tail was padded with samples from inside the line.
- When the value at a line end is missing, the nearest available value is used as the mirror point, so a missing end
  value does not produce NaN.
- The filter is symmetric for every length, so the result is not shifted along the line for even lengths.
- The filter keeps the level of the series (unit gain) and the result is no longer rescaled by root mean squares.
- The filter length used on a line is limited by the line length instead of half of it; short lines are smoothed more
  than before when the entered length exceeds half of the line.
- **BREAKING** for results only: re-applying the low-pass filter to the same series gives slightly different values
  than before, everywhere on a line and most at line ends.

## Non-goals

- Changing the filter's cutoff for a given length, its window shape or its parameter range (0..10000) in the tool and
  the MCP `apply_filter` tool.
- Changing how missing values inside a line are handled: they are still filled from their neighbors in `_LPF`.
- Changing the time lag or running median filters.
- Making series filters undoable or changing how the `_LPF` column is created.
- Re-filtering `_LPF` series that were saved with the old filter.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `filter-tools`: adds the output requirements of the low-pass filter: no NaN for short lines or length 1, no shift
  along the line, unchanged level, linear trends kept at line ends.

## Impact

- `math/filter`: `FirFilter` builds a symmetric kernel of odd length and filters a line with point-mirrored ends and
  no delay; `LowPassFilter` limits the length per line and no longer rescales.
- `SensorLineChart.applyFilter`, `LowPassTool` and the MCP `ApplyFilter` tool are unchanged.
- New unit tests for `LowPassFilter`.
- No changes to file formats, events, threads, undo or dependencies.
