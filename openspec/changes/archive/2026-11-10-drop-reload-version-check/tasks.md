# Tasks

## 1. Close through the model

- [x] 1.1 Add `Model.closeFile(file)`: remove from `FileManager`, publish `FileClosedEvent`, `justdraw`, `traceValues`; verify it compiles and has no callers that publish the event themselves
- [x] 1.2 In `Model.removeChart` call `updateAuxElements` after removing the chart and select the first `FileManager` file that still has a chart; verify by reading that closing the first of several files selects another file
- [x] 1.3 Switch `Chart.close` and `Saver.onCloseAll` to `model.removeChart(file)` then `model.closeFile(file)`; verify `grep "new FileClosedEvent"` finds only `Model`
- [x] 1.4 Reduce `ProfileView.fileClosed` to resetting `currentFile`, `activateTools` and the channel combo box; verify it no longer touches `FileManager`, charts or publishes events

## 2. Replace inside initChart

- [x] 2.1 In `Model.initChart` replace an open file by `closeFile(oldChart.getFile())`, master's `createChart` (keeps the position) and disposing the old chart; remove `Model.replaceFile` and restore `createChart` to master; verify `git diff master -- Model.java` touches only `initChart`, `closeFile` and `removeChart`
- [x] 2.2 Restore `Loader` to master and add only the reopen check in `openFile` (unmodified → reload, folder → keep with status, otherwise `confirmReload` with Keep selecting the chart); verify the master open paths are unchanged
- [x] 2.3 Update `spec/overview.md` (Reopening: no `replaceFile`, no version skip; Events: `FileClosedEvent` is published by `Model.closeFile` after the file left the model); verify no mention of `replaceFile` remains in `spec/`

## 3. Verify

- [x] 3.1 Run `mvn clean package` and `mvn test`; fix new compilation errors and Error Prone/NullAway warnings
- [x] 3.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the completeness reference; fix critical and major issues and report the scores and any issues left open
