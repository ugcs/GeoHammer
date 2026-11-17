## 1. Rejection in the service

- [x] 1.1 In `GriddingService.runGridding`, after the grid width and height are computed, throw an
  `IllegalArgumentException` when their product exceeds `MAX_GRID_CELLS` (50 million), before anything is
  allocated; verify existing `GriddingServiceTest` tests pass
- [x] 1.2 Add `GriddingServiceTest` cases: the survey with a cell size that exceeds the limit is rejected;
  the survey with one extra row at latitude 0 and longitude 0 is rejected; a rejected
  call returns within a time bound; verify the tests pass

## 2. Tools

- [x] 2.1 In `FilterToolView.submitAction`, hide the warning when an action succeeds and no other warning was shown
  or hidden since the action was submitted (`warningVersion`), remove the hiding at the start of the action in
  `GriddingTool.applyGridding`, and in the `catch` of the action
  post `showWarning(message, false)` to the FX thread, with a fallback text for an exception without a message and
  nothing for an interrupted action; verify by reading the flow that the exception is still logged and rethrown
- [x] 2.2 In `GriddingTool`, override `updateView()` to call `onInputChange()` so that a file change updates the
  banner; verify by reading the flow that the banner is cleared by input, file and series changes and by the start
  of the next gridding, and is kept by filter option changes
- [x] 2.3 In `GriddingTool.applyGridding`, read the display filter before the action is submitted and publish it
  inside the action only when the gridding returned a result, instead of calling `publishFilter()` before the
  gridding; verify by reading the flow that a rejected gridding fires no `GridUpdatedEvent` and the inputs keep the
  typed values

## 3. Descriptions and specs

- [x] 3.1 State in the description of `run_gridding` in `mcp/tool/RunGridding` that a grid above an internal limit
  (currently 50 million cells) is rejected; verify
  the text reads correctly in the tool schema
- [x] 3.2 Add the limit to the Grid entry of the glossary in `spec/overview.md`; verify the entry matches the delta
  spec

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings; verify the build
  and all tests pass
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
