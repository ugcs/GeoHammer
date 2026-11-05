# Design

## Context

Every UI open path ends in `Loader.load(files, interactive)`: the Open dialog (`Saver.onOpen`), drag and drop, the
command line argument and the macOS open-file handler (`MainGeoHammer`). On a background thread the loader resolves a
meta file to its source, builds a new `SgyFile` with `open(...)` and queues `Model.initChart(file)` on the FX thread.

`SgyFile.equals` compares only the `File`, so `initChart` finds the chart of an already open copy and takes its
"recreate chart" branch: it drops the old instance from `FileManager` and the chart map and builds a new chart, without
`FileClosedEvent` and without any prompt. This branch has no other caller. MCP `open_file` already checks
`FileManager.getFile` first and selects the open chart.

Closing a file goes through `Chart.close()`: `dispose()` and `FileClosedEvent`, whose listeners drop per-file state
(`UndoModel`, `GridLayer`, `McpServer`, ...) and remove the file and its chart (`ProfileView` → `FileManager.removeFile`,
`Model.removeChart`). All of them match the file by `equals`.

## Goals / Non-Goals

**Goals:**
- A reload is a regular close followed by a regular open, so no per-file state can stay bound to a replaced instance.
- The question about unsaved changes is asked before the file is read.
- The open file is not replaced when it changed while the fresh copy was read.

**Non-Goals:**
- Reloading a file in place or undoing a reload (see proposal).
- Changing script result handling (`Loader.loadFrom`).

## Decisions

### D1. Detect the open file in `Loader.openFile`, after meta resolution

`openFile` looks up `model.getFileManager().getFile(file)` in the branch for a concrete data file (a
meta file recurses into its source first, so the lookup sees the data file). `FileManager` holds a
`CopyOnWriteArrayList`, so reading it on the background thread is safe.

*Alternative:* ask in `Model.initChart`. Rejected: the file would be fully parsed before the question, and
`initChart` would need to know whether the load is interactive.

### D2. Reopen flow (`Loader`)

    [background]
      openFile = fileManager.getFile(file)
      version = openFile.getVersion()
      if openFile.isUnsaved():
          if !interactive          -> status "<name> has unsaved changes and was kept"; return
          if askReload() == KEEP   -> select chart; return
          version = openFile.getVersion()
      reloadedFile = readFile(file)                    // same construction as a first open (D3)
    [FX, awaited by the loader thread]
      replaced = model.replaceFile(openFile, reloadedFile, version)     // D4
    [background]
      if !replaced: status "Reload of <name> skipped: the file was changed or closed"

The version is read before the unsaved check, so an edit made right after the check makes the replace skip instead of
being discarded without a question. After the dialog it is read again: edits made while the dialog is shown are part of
what the user agreed to discard.

The loader waits for the replace, so parser warnings are shown only for a replaced file and a skipped reload is
reported. A reopened file returns `Result.success(file)` and is listed in `FileOpenedEvent` whether it was reloaded,
kept or skipped: listeners only refresh their state for an unchanged file, and telling the outcomes apart would cost a
result type in the loader.

*Alternative:* reload in place by moving the state of the fresh instance into the open one (`reloadFrom`), with an
undo snapshot. Rejected for its size: a state transfer per file type and new snapshots for raw data, for the benefit of
an undoable reload and kept per-file state, which are non-goals.

### D3. Reading a file instance separately from adding its chart

The `open*File` methods return the opened instance instead of queuing its chart; `readFile` picks the method by file
type. The step after it differs: a first open queues `initChart`; a reopen queues `Model.replaceFile`.
A reloaded CSV file matches its template again, as on a first open.

### D4. `Model.replaceFile(SgyFile openFile, SgyFile reloadedFile, long version)` on the FX thread

    if the file open under that path does not have the given version -> return false
    index = position of the chart of openFile in the chart container
    remove the chart and openFile from the chart map, the container and FileManager; chart.dispose()
    publishEvent(new FileClosedEvent(this, openFile))     // per-file state is dropped
    create the chart of reloadedFile at index; fileManager.addFile(reloadedFile)
    updateAuxElements(); select and scroll to the new chart; return true

`Model` owns the open files and their charts, so it checks the version and removes the open file itself instead of
relying on the `ProfileView` close listener; that listener then finds nothing left to remove. Versions are unique
across files, so a matching version means the same instance is still open and unchanged. `FileClosedEvent` listeners
run synchronously and match by `equals`, so they drop the state of the open file before the fresh file, equal to it,
is added. The map viewport is not re-centered (`initField` is not called): the reloaded file covers the same area.

### D5. Blocking Keep/Reload dialog

`Dialogs.confirm` waits on the loader thread the way `TemplateEditor.createTemplate` does: `CompletableFuture`, the
alert shown with `Platform.runLater`, `join()`. Buttons "Keep" and "Reload", owner `AppContext.stage`, no default
button. Closing the dialog means Keep.

### D6. `Model.initChart` no longer recreates a chart

The recreate branch becomes a guard: if the file already has a chart, select it and return it; the passed instance is
not added. With D1 this is reached only by a race of two loads of the same file.

## Risks / Trade-offs

- [A silent reload of an unmodified file drops its undo history and grid] → accepted; the file on disk is the same
  state, and the drop is the regular close behavior.
- [Closing selects the first remaining chart before the new chart is selected] → a transient `FileSelectedEvent`; the
  new chart is selected in the same FX task.
- [The loader thread waits for the FX thread during a reload] → nothing on the FX thread waits for the loader, so it
  cannot deadlock; `Dialogs.confirm` already waits the same way.
- [Reload of an unmodified large GPR file re-reads it fully] → accepted; skipping by modification time is a non-goal.
