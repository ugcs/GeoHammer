## 1. Fill missing values

- [x] 1.1 Add a `MissingValues` method that fills empty cells of a `List<GeoData>` per line run, for columns with
  `display = true` except the Line semantic, using `Interpolator.linear()` over row timestamps (design decisions 2–5,
  8); verify it compiles and that String cells and non-empty cells are never written
- [x] 1.2 Call it in `CsvFile.open` after `reorderLines()` / `mergeSinglePointLines()` and before `setUnsaved(false)`,
  without reporting the fill; verify the call order in the diff
- [x] 1.3 Spread rows sharing a timestamp evenly up to the next distinct time when building the line's time axis
  (design decision 4); verify by tracing the "Rows sharing a timestamp" and "All rows share one timestamp" scenarios
  through the code

## 2. Verify

- [x] 2.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings; verify the build
  succeeds with no new warnings in the changed files
- [x] 2.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
  uncommitted changes with this change's proposal and specs as the completeness reference; fix critical and major
  issues and report the scores and any issues left open
