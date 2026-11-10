## Why

The sensor chart gives series with similar values a shared vertical axis, so they can be compared on one scale
(#1070). The Line series is grouped this way too, although it holds survey line indices rather than a measurement. In
a MagNIMBUS file the `Next WP` line index (0..16) shares an axis with `GyroVect`, whose values mostly fall in 0..19,
and one `GyroVect` spike of 399 stretches the shared axis to 0..400. The line index then fills a few percent of the
plot height.

## What Changes

- The Line series always keeps its own vertical axis, built from its own values. It is never grouped with other series,
  so it no longer links them into a group either.
- The other series are grouped as before.

## Non-goals

- Changing how similarity between series is measured or its threshold.
- Keeping a series out of a group when the shared axis is much wider than its own values (for example, stretched by
  another series' spike).
- Comparing series units before sharing an axis.
- Changing how a group that is joined only through intermediate series is formed.
- Changing the axis of the Line series in other charts, or the map.

## Capabilities

### New Capabilities

- `sensor-chart-axes`: how the sensor chart chooses vertical axis ranges, which series share an axis and the Line
  series kept on its own axis.

### Modified Capabilities

None.

## Impact

- `SensorLineChart.mergeDisplayRanges` skips the Line series when building groups of series that share an axis.
- No unit test: building a `Plot` needs the application context for its theme color.
- No changes to file formats, events, threads, undo or dependencies.
