## Context

`SensorLineChart.mergeDisplayRanges()` groups the charts of a file: two `Plot`s are linked when the intersection over
union of their robust (spike-trimmed) ranges is at least `RANGE_MERGE_SIMILARITY_THRESHOLD`, groups are the connected
components of these links, and every plot of a group of two or more gets `Plot.buildDisplayRange` of the union of the
group's full data ranges. It runs on the FX thread from the constructor, `reload()` and `applyFilter()`. The Line
series has a chart like any other display column; `lineSeriesName` already holds its header.

## Goals / Non-Goals

**Goals:**
- Leave the Line series out of grouping with the smallest change to `mergeDisplayRanges()`.

**Non-Goals:**
- Changing the similarity measure, the threshold or the display range of a group.
- Restructuring the grouping code.
- Unit tests for the grouping: it is private to the chart, and building a `Plot` needs the application context for its
  theme color.

## Decisions

### Exclude the Line series before building links

The Line chart is removed from the list of grouped charts before the link graph is built, so it has no links: it never
joins a group and never connects two other charts. Its display range stays the one its `Plot` was built with, from its
own data.

Alternative: build groups as now and remove the Line chart from its group afterwards. Rejected: the remaining charts
would still be grouped through the Line chart, against the spec.

## Risks / Trade-offs

- [The Line series looked aligned with other series before and some users may rely on it] → The line index is not a
  measurement; a shared axis with a physical value has no meaning. The Line chart keeps its own readable range.
- [Plots kept in a group of the previous load] → `reload()` recreates every `Plot` with its own display range before
  grouping and re-applies the viewport to all charts; since the Line plot is never grouped, its range is always its own.
