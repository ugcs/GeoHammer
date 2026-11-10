# Design

## Context

See proposal.md - Why. Today `Chart.close` and `Saver.onCloseAll` publish `FileClosedEvent`, and
`ProfileView.fileClosed` does the close: removes the file from `FileManager`, calls `Model.removeChart`, updates aux
elements and publishes `justdraw`/`traceValues`. `Model.replaceFile` publishes the same event during a reload, so
`ProfileView` closes the file a second time in the middle of the replace. `Loader.reopenFile` reads the fresh instance
off the FX thread and calls `Model.replaceFile` with the version taken before reading.

## Goals / Non-Goals

**Goals:**
- `FileClosedEvent` is a notification published by `Model` after the file left the model; listeners only drop their
  own per-file state.
- One entry point for adding a chart: `Model.initChart`, which also replaces an open file.

**Non-Goals:**
- Merging `removeChart` and `closeFile` into one call.
- Reworking how `Model.fileClosed` clears selected traces.

## Decisions

**Close is a pair of model calls.** `Chart.close` (after its unsaved prompt) and `Saver.onCloseAll` call
`model.removeChart(file)` then `model.closeFile(file)` on the FX thread.
- `removeChart`: removes the chart from `charts` and the container, disposes it, calls `updateAuxElements`, and selects
  the first file of `FileManager` that still has a chart, otherwise publishes `FileSelectedEvent(null)`. The closed file
  is still in `FileManager` at this point, so picking `getFirst()` blindly would select nothing when it is first.
- `closeFile`: removes the file from `FileManager`, publishes `FileClosedEvent`, `WhatChanged.justdraw` and
  `WhatChanged.traceValues`. The repaint goes last, after charts and aux elements reached their final state.
- Alternative `closeFile` → `removeChart` was rejected: the repaint would run before aux elements of the closed file
  are dropped.

**Replace inside `initChart`.** When `getChart(file)` returns a chart (equality of `SgyFile` is by path), `initChart`
removes it from `charts`, calls `closeFile(oldChart.getFile())` with the old instance, creates the new chart at the old
chart's container index (or appends it), always disposes the old chart, then adds the file, updates aux elements,
`initField` and selects the new chart. Without an open chart it behaves as before. `Model.replaceFile` is removed.
- Alternative: keep `replaceFile` with a version argument. Rejected in review to keep the model API small.

**No version check.** `Loader.reopenFile` keeps the Keep/Reload prompt and folder handling, reads the fresh instance
and schedules `model.initChart(reloaded)` with `Platform.runLater`, as for a new file. The blocking
`CompletableFuture` around the replace goes away; parser warnings are shown after the chart init as for a new file.

**`ProfileView.fileClosed`** keeps only resetting `currentFile`, `activateTools` and the channel combo box.

Threads: all of the above runs on the FX thread; reading the file stays on the loader's background thread. No file data
changes, so snapshots, `setUnsaved` and cache invalidation are not involved; undo frames of the replaced file are dropped
by `UndoModel` on `FileClosedEvent`.

## Risks / Trade-offs

- [Edits made while a reload is read are lost] → accepted, see proposal.md; the window is the read time of the file.
- [`Model.fileClosed` sees no chart for the closed file and clears selected traces that have no chart] → correct for
  the closed file; left as is to keep the change small.
- [Two opens of the same path racing] → the second `initChart` replaces the first chart, so there is still one chart.
