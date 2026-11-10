## 1. Grouping

- [x] 1.1 In `SensorLineChart.mergeDisplayRanges()`, remove the chart of `lineSeriesName` from the grouped charts
  before the link graph is built; verify by compiling

## 2. Verify

- [x] 2.1 Run `mvn clean package`; fix new compilation errors and Error Prone/NullAway warnings, and verify all tests
  pass
- [x] 2.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference; fix critical and major issues, and report the scores and any issues left open
