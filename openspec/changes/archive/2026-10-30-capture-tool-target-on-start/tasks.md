# Tasks

## 1. Preferences

- [x] 1.1 In `FilterToolView.submitAction`, call `savePreferences()` on the calling thread before `executor.submit`
  instead of inside the task; verify no task body calls `savePreferences()` (`grep`)

## 2. Filter tools

- [x] 2.1 In `LowPassTool`, `RunningMedianTool` and `TimeLagTool` `onApply`, read the selected series name before
  `submitAction` and pass it to the task; verify the task lambdas no longer call `getSelectedSeriesName()`
- [x] 2.2 In the same three tools' `onApplyToAll`, capture the selected file and build the list of target
  `SensorLineChart`s of its template with a `for` loop on the FX thread, then apply the filter to that list in the
  task; verify the task lambdas no longer reference `selectedFile` or `model` (`grep`)

## 3. Quality control and gridding

- [x] 3.1 In `QualityControlTool.onApply`, capture `selectedFile` (return if null) and `getParams()` before
  `submitAction`, and use them in the task; verify against the "Selection cleared during quality control" scenario
- [x] 3.2 In `QualityControlTool.onApplyToAll`, capture the file and params and collect the files of its template on
  the FX thread before `submitAction`; verify the task lambda no longer references `selectedFile` or input controls
- [x] 3.3 In `GriddingTool.applyGridding`, call `publishFilter()` before `submitAction` instead of inside the task;
  verify against the "Another file selected during gridding" scenario

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 4.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
  uncommitted changes with this change's proposal and specs as the completeness reference, fix critical and major
  issues, and report the scores and any issues left open
