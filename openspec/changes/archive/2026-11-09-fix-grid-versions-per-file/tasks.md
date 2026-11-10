## 1. Shared flag

- [x] 1.1 Add `boolean shared` to `GriddingResult` and set it in `GriddingService.runGridding` to `files.size() > 1`.
  Verify with `mvn compile`.
- [x] 1.2 Add `GriddingServiceTest` checking that gridding of several files gives a shared result and gridding of one
  file an individual result. Verify with `mvn -Dtest=GriddingServiceTest test`.

## 2. Replacement rule

- [x] 2.1 In `GridLayer.setResult`, after putting the result, remove the result of every other open file of the same
  template whose `shared` flag differs, using `results.remove(file, result)`. Verify with `mvn compile`.

- [x] 2.2 Skip a null result in `GridLayer.setResult`, so that an empty or cancelled gridding keeps the current grids.
  Verify with `mvn compile`.

## 3. Time-lag regrid

- [x] 3.1 In `GriddingTool.onSeriesUpdated`, regrid the open files of the template when the current result is shared,
  and the selected file otherwise. Verify with `mvn compile`.

## 4. Verify

- [x] 4.1 Run `mvn clean test` and fix new compilation errors and Error Prone/NullAway warnings in changed files.
