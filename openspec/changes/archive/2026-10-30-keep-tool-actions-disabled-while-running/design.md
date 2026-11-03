## Context

`FilterToolView` owns the Apply / Apply to all buttons and the input container of every processing tool. Two
unrelated reasons write the same `setDisable` state, and the last write wins:

```
 Apply --> submitAction --> disableAndShowProgress --> disableInput(true), disableActions(true)
                                                        |  task on the shared executor
 selectFile --> loadPreferences --> setText --> listener --> validateInput --> disableActions(false)
 SeriesSelectedEvent (GriddingTool) --> onInputChange -------------------------> disableActions(false)
 GprElevationTool.updateView ---------------------------------------------------> disableInput(profile == null)
                                                        |
 task done --> runLater(enableAndHideProgress) --> disableInput(false), disableActions(false)
```

- Validation: `LowPassTool`, `RunningMedianTool`, `TimeLagTool` (`validateInput`), `GriddingTool` (`onInputChange`),
  `QualityControlTool` (`validateInput`, always valid).
- `GprBackgroundTool` and `GprElevationTool` keep their own buttons inside `inputContainer`, so input disabling covers
  them. `GprElevationTool` also disables input when the file has no ground profile, and the task completion re-enables
  it today.
- `ScriptExecutionTool` never validates; it calls `disableAndShowProgress` / `enableAndHideProgress` from
  `refreshExecutionStatus` per selected file and from its run callbacks (via `Platform.runLater`), and relies on
  `enableAndHideProgress` to enable its Apply buttons.
- All writers run on the FX thread: button handlers, `selectFile` (via `runLater`), the completion `runLater` in
  `submitAction`. `SeriesUpdatedEvent` is published only by `SensorLineChart.applyFilter` inside `Platform.runLater`, so
  `GriddingTool.onSeriesUpdated` and its automatic regrid also run on the FX thread.

## Goals / Non-Goals

**Goals:**
- Button and input state is derived from the tool's running state and the tool's own request, in one place.
- The automatic regrid checks the gridding tool's running state.

**Non-Goals:**
- A shared lock or queue across tools, or between tools and MCP.
- Changing per-file running semantics of script execution.
- Cancelling a running task.

## Decisions

### Separate running state from tool requests in `FilterToolView`
`FilterToolView` keeps three FX-thread-confined booleans and derives the controls from them:

```
 running        set by disableAndShowProgress, cleared by enableAndHideProgress
 inputValid     set by the tool's validation          (default true)
 inputDisabled  set by the tool, e.g. no ground profile (default false)

 updateControls():
   applyButton, applyToAllButton  disabled <=> running || !inputValid
   inputContainer                 disabled <=> running || inputDisabled
```

- `disableActions(boolean)` is replaced by `setInputValid(boolean)`, which is what every caller means; validating
  tools call it instead. `QualityControlTool` calls `setInputValid(true)`.
- `disableInput(boolean)` becomes `setInputDisabled(boolean)` and records the tool's request; it no longer enables the
  inputs while running.
- `disableAndShowProgress` / `enableAndHideProgress` call `setRunning`, which updates the controls; completion
  therefore restores the tool's own state instead of enabling everything.
- A protected `isRunning()` lets `GriddingTool` check the state.
- `inputValid` defaults to `true` so tools that never validate (script execution) are enabled by
  `enableAndHideProgress` as today. The constructor keeps the buttons disabled until the first update, as today.

Plain booleans instead of atomics: every reader and writer is on the FX thread (see Context).

Alternative: JavaFX `BooleanProperty` values with `disableProperty().bind(running.or(inputValid.not()))`. Rejected: a
bound property throws on `setDisable`, so any later direct call on these buttons would fail at runtime, and it adds
binding plumbing for three booleans.

Alternative: only skip `disableActions(false)` while running, inside each tool. Rejected: the check would be repeated
in five tools, and completion would still enable the buttons for invalid input.

### Running state is per tool
One task per tool at a time, whatever file is selected. The progress indicator is already shared by the tool, and the
tool's inputs belong to the selected file's template, so a second task for another file would read inputs that are
not its own. Script execution keeps its per-file behavior because `refreshExecutionStatus` sets the state on every file
selection.

### Automatic regrid is skipped while gridding runs
In `GriddingTool.onSeriesUpdated`, the resubmit condition adds `!isRunning()`; when the grid series is updated while
running, the existing `showParamsChangedWarning(true)` branch runs. The running task hides the warning only when it
starts. On completion, `onGridUpdated` syncs the param inputs with the new grid; it rewrites them only when the values
differ, as `updateFilterInputs` does, because rewriting `2` as `2.0` fires `onInputChange`, which recalculates the
warning from series and params alone and would hide it.

Alternative: queue one regrid and run it on completion. Rejected: the user may select another file by then and
`applyGridding` reads the current inputs, which needs extra file checks; the case is rare and the warning already
covers it.

Alternative: `SinglePendingExecutor`. Rejected: its `submit(Callable)` never completes the future of a replaced pending
task, so a task registered in `TaskService` would stay in the status bar.

## Risks / Trade-offs

- [A task whose completion never reaches `enableAndHideProgress` leaves the tool disabled] → `submitAction` clears the
  state in `finally`, as today; no new path is added.
- [Script execution switches `running` on file selection, so a script running for file A does not block Apply on file
  B] → intended per-file behavior, unchanged.
- [The running grid may read a time-lagged series while the time lag rewrites it] → the warning is shown, so the user
  recomputes; data races between tools are a non-goal.
