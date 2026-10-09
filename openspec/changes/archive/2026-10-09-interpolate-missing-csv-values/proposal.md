## Why

CSV sensor files often have empty cells: mixed-rate logs interleave rows from different sources (e.g. GPS-only rows
between magnetometer rows), and sensors drop samples. Today only latitude and longitude are filled on load; every
other series shows gaps in the chart and leaves empty cells for gridding and filters. Issue #919.

## What Changes

- On load of a CSV or fixed-width file, empty cells of displayed data columns are filled by linear interpolation.
- Interpolation runs within each survey line and never across line boundaries.
- The interpolation axis is the row timestamp when the file has times; otherwise it is the row index.
- At the head and tail of each line, empty cells take the first and last known value of that line.
- Non-numeric text cells are kept as they are and are not used as interpolation anchors.
- The fill is silent: the file still opens as unmodified, and filled values are written to the file on the next save.

## Non-goals

- NMEA and sonar files: their sources are never rewritten; filling them is a separate question.
- Rounding or type preservation for integer-valued columns: filled values are written as decimals.
- Re-filling after edits (line split/merge, series filters, MCP column import): the fill runs on load only.
- Changing the existing latitude/longitude gap filling.
- Marking filled cells or making the fill optional.
- Notifying the user about filled values: most source files have gaps, so a message would appear on almost every load;
  the existing position fill is silent too.

## Capabilities

### New Capabilities

- `csv-loading`: behavior of opening CSV and fixed-width data files; this change adds filling of missing values.

### Modified Capabilities

None.

## Impact

- `CsvFile.open` (load sequence) and `MissingValues` (gap-filling utility); reuses the existing
  `LinearInterpolator`. `Interpolator` factories return shared instances instead of new ones.
- Saved CSV files contain interpolated values where the source had empty cells.
