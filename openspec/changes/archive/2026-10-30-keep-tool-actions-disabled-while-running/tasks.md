# Tasks

## 1. Running state in FilterToolView

- [x] 1.1 In `FilterToolView`, add the `running`, `inputValid` (default `true`) and `inputDisabled` fields and a private
  `updateControls()` that disables Apply / Apply to all when `running || !inputValid` and the input container when
  `running || inputDisabled`; verify the class compiles
- [x] 1.2 Replace `disableActions(boolean)` with `setInputValid(boolean)` and `disableInput(boolean)` with
  `setInputDisabled(boolean)`, make `disableAndShowProgress` / `enableAndHideProgress` call `setRunning`, all through
  `updateControls()`; add a protected `isRunning()`; verify no caller of `disableActions` remains (`grep`)

## 2. Tools

- [x] 2.1 In `LowPassTool`, `RunningMedianTool`, `TimeLagTool` and `GriddingTool.onInputChange`, call
  `setInputValid(valid)` instead of `disableActions(disable)`; in `QualityControlTool.validateInput` call
  `setInputValid(true)`; verify the project compiles
- [x] 2.2 In `GriddingTool.onSeriesUpdated`, resubmit only when `!isRunning()`, otherwise show the params changed
  warning; verify the condition matches the "No automatic regrid while gridding runs" requirement in the spec
- [x] 2.3 In `GriddingTool.onGridUpdated`, update the param inputs only when the grid params differ from the current
  input values, so the warning shown during gridding is not hidden on completion; verify against the "Time lag applied
  during gridding" scenario

## 3. Verify

- [x] 3.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 3.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
  uncommitted changes with this change's proposal and specs as the completeness reference, fix critical and major
  issues, and report the scores and any issues left open
