## Context

`ToolView.selectedFile` is a plain field written on the FX thread by `selectFile` (via `Platform.runLater` from the
`FileSelectedEvent` listener), which also calls `loadPreferences()` and rewrites the input fields. Tasks run on the
shared virtual-thread executor through `FilterToolView.submitAction`. Every caller of `submitAction` runs on the FX
thread: button handlers, and `GriddingTool.onSeriesUpdated`, whose `SeriesUpdatedEvent` is published inside
`Platform.runLater` in `SensorLineChart.applyFilter`.

Tool state read inside a task today:

```
 FX thread (click)                     task on the executor
 -----------------                     --------------------
 LowPass/RunningMedian/TimeLag
   Apply:      chart, value            savePreferences()      selectedFile, input text
                                       getSelectedSeriesName()
   Apply all:  chart, series, value    savePreferences()
                                       getSensorCharts(), Templates.equals(c, selectedFile)  per chart
 QualityControl
   Apply:      null check              savePreferences(), getParams(), List.of(selectedFile)
   Apply all:  null check              savePreferences(), getParams(), Templates.equals(f, selectedFile)
 Gridding
   Apply(all): file, series, files,    savePreferences()
               params                  publishFilter()        selectedFile, series, filter inputs
```

`GriddingTool.onApplyToAll` and `GprElevationTool.flattenSurface` already read their target on the FX thread and pass
it to the task; this change applies the same pattern to the others.

File data handling is unchanged: `SensorLineChart.applyFilter` adds a column, sets values, calls `setUnsaved(true)`
and reloads the chart as today. Series filters stay not undoable.

## Goals / Non-Goals

**Goals:**
- A task never reads `selectedFile`, the selected series or input controls; everything it needs is read on the FX thread
  before `submitAction` and passed in as effectively final locals.

**Non-Goals:**
- Making `selectedFile` volatile or otherwise thread-safe: once no task reads it, it is FX-confined.
- Moving UI updates that tasks already do (`qualityLayer.setIssues`, `showParamsChangedWarning`) to the FX thread.

## Decisions

### Save preferences before the task is submitted
`FilterToolView.submitAction` calls `savePreferences()` on the calling (FX) thread before `executor.submit`, instead of
as the first statement of the task. Every `savePreferences` implementation reads `selectedFile` and input controls, and
`Settings` writes are in-memory (flushed on shutdown), so the call is cheap on the FX thread.
`GriddingTool.applyFilter` already calls it on the FX thread.

Alternative: capture the template name and values in each tool and save them in the task. Rejected: four tools would
each need a value object for their inputs only to move a cheap call off the thread that owns the controls.

### Capture the target in each tool's handler
- `LowPassTool`, `RunningMedianTool`, `TimeLagTool`:
  - Apply reads `sensorChart.getSelectedSeriesName()` before `submitAction`.
  - Apply to all builds the list of target `SensorLineChart`s on the FX thread: a `for` loop over
    `model.getSensorCharts()` keeping charts with `Templates.equals(chart.getFile(), file)`, where `file` is the
    captured selected file. The task then applies the filter to each chart in the list.
- `QualityControlTool`:
  - Apply captures `SgyFile file = selectedFile`, returns early if it is null, and reads `getParams()` before
    `submitAction`. The task calls `createQualityChecks(params)` (pure) and checks `List.of(file)`.
  - Apply to all does the same and collects the files of the captured file's template from
    `model.getFileManager().getFiles()` on the FX thread.
- `GriddingTool.applyGridding` calls `publishFilter()` before `submitAction`, next to `getParams()`.
  `publishFilter` already runs on the FX thread from `applyFilter`.

The chart list is built at click time rather than when the task starts. Apply to all therefore means "the matching
files open when the user clicked": a file opened during the run is not processed, and the result does not depend on
executor scheduling.

Alternative: capture only the file and keep the lazy stream in the task. Rejected: it fixes the template mismatch but
keeps reading `Model` charts from the task, and whether a file opened during the run is processed would depend on
timing.

Alternative: a shared helper (e.g. in `FilterToolView`) returning the charts or files of a template. Rejected: three
filter tools need charts, quality control needs files, and gridding already has its own two lines; a helper for a
loop of four lines is premature.

## Risks / Trade-offs

- [A file closed during Apply to all is still in the captured list, so the filter writes to a closed file] → same as
  today, where the lazy stream can also pick a chart just before it closes; listed as a non-goal.
- [`savePreferences` now runs before the task even when `submitAction` is used without progress] → no caller uses that
  variant with different expectations; preferences were always saved at task start.
- [The FX thread now iterates charts and files on click] → a few hundred entries at most, no I/O.
