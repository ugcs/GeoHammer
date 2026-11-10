# Tasks

## 1. Close through the model

- [x] 1.1 Add `Model.closeFile(file)`: remove from `FileManager`, publish `FileClosedEvent`, `justdraw`, `traceValues`; verify it compiles and has no callers that publish the event themselves
- [x] 1.2 In `Model.removeChart` call `updateAuxElements` after removing the chart and select the first `FileManager` file that still has a chart; verify by reading that closing the first of several files selects another file
- [x] 1.3 Switch `Chart.close` and `Saver.onCloseAll` to `model.removeChart(file)` then `model.closeFile(file)`; verify `grep "new FileClosedEvent"` finds only `Model`
- [x] 1.4 Reduce `ProfileView.fileClosed` to resetting `currentFile`, `activateTools` and the channel combo box; verify it no longer touches `FileManager`, charts or publishes events

## 2. Replace inside initChart

- [x] 2.1 Rework `Model.initChart`: for an open chart remove it from `charts`, `closeFile(oldChart.getFile())`, put the new chart at the old index or append, always dispose the old chart; remove `Model.replaceFile` and the `index` parameter of `createChart` if unused; verify it compiles
- [x] 2.2 In `Loader.reopenFile` drop the version capture and the blocking replace, schedule `model.initChart(reloaded)` with `Platform.runLater` and show parser warnings as for a new file; verify the Keep/Reload prompt and folder status message paths are unchanged
- [x] 2.3 Update `spec/overview.md` (Reopening: no `replaceFile`, no version skip; Events: `FileClosedEvent` is published by `Model.closeFile` after the file left the model); verify no mention of `replaceFile` remains in `spec/`

## 3. Verify

- [x] 3.1 Run `mvn clean package` and `mvn test`; fix new compilation errors and Error Prone/NullAway warnings
- [x] 3.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the completeness reference; fix critical and major issues and report the scores and any issues left open
