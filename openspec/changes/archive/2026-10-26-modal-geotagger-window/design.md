# Design

## Context

See proposal.md - Why. Current state that shapes the approach:

- `GeotaggerView` creates its own `Stage` with `Modality.NONE` and no owner. `processFiles()` runs
  `Geotagger.geotag(...)` on the shared executor and passes the live `ObservableList`s of `DataFilePane` and
  `PositionFilePane`. Close stays enabled during processing; `closeWindow()` clears both lists and closes the stage.
- `ProfileView.showGeotaggerWindow` replaces the stage's close-request handler with its own (`geotaggerStage = null`),
  so the window close button reaches `closeWindow()` only through `setOnHiding`. Esc is not handled.
- `FilePane.addFile` reuses the `SgyFile` instance of the main window when the file is open there, otherwise loads its
  own instance from disk.
- `Geotagger.geotag` processes data files one by one: for a file open in GeoHammer it removes its undo snapshots,
  writes positions into the rows (`updatePositions`), copies them to traces for meta files (`copyPositionsFromMeta`),
  sets unsaved and schedules `model.reload` on the FX thread; for any other file it saves it to its source path.
- Saving is not atomic: `CsvFile.save` writes the source file in place, so an interrupt during the write
  (`ClosedByInterruptException` on NIO channels) leaves a truncated file.
- Interpolating the rows of one file takes milliseconds; the long parts of a run are loading positions and saving
  files that are not open in GeoHammer.

## Goals / Non-Goals

**Goals:**
- No UI path can close, open or edit a file while the geotagger holds it.
- Cancellation never leaves a half-written file on disk or half-updated positions in memory: it stops only
  between files.

**Non-Goals:**
- Making the file lists react to `FileClosedEvent` (modality makes it unnecessary).
- Moving the mutation of open files off the background thread; it stays as today.

## Decisions

### 1. Application-modal window owned by the main stage
Before the first show, `GeotaggerView` calls `initOwner(AppContext.stage)` and
`initModality(Modality.APPLICATION_MODAL)`. Application modality also blocks utility windows (for example scripts),
which could modify or reload a listed file. Precedent: `TaskRunner`. The owner keeps the window in front of the main
window on macOS.

*Alternative:* `WINDOW_MODAL` blocks only the main window and leaves utility windows usable — rejected for the reason
above.

`ProfileView` no longer keeps `geotaggerStage` or replaces the close-request handler: with a modal window the
Geotagging button cannot be pressed while the window is open, so it just calls `geotaggerView.showWindow()`
(which no longer needs to return the stage).

### 2. One close path, deferred while processing
Close button and window close request go through one handler in `GeotaggerView`:

```
 requestClose
   idle       -> closeWindow()                           (as today)
   processing -> consume event; cancelRequested.set(true);
                 Close disabled; label "Cancelling..."
 task end (FX) -> hideProgress(); if cancelRequested -> closeWindow()
```

The window close request is consumed while processing, so the stage stays shown and modal until the background task
has returned. `setOnHiding` stays only as a safety net for list clearing.

*Alternative:* a separate Cancel button (Process turning into Cancel) — rejected by the user: Close and Cancel side by
side confuse.

### 3. Cooperative cancellation with a flag, not interrupt
`GeotaggerView` owns an `AtomicBoolean cancelRequested`, reset on every Process and passed to
`Geotagger.geotag(dataFiles, positionFiles, onProgress, cancelRequested)`. The task is never cancelled with
`Future.cancel(true)`, so a started save or reload always completes.

*Alternative:* `future.cancel(true)` and `Thread.isInterrupted()` like `Loader` — rejected: an interrupt during
`save` can truncate the source file.

### 4. Cancellation between files
`geotag` checks the flag before each data file and throws `CancellationException` when it is set, as `Loader` and
`GprFile` do. A file that has started is processed as before: positions, `copyPositionsFromMeta`, then `reload` for an
open file or `save` for another file. No snapshot or rollback is needed, and `updatePositions` stays unaware of
cancellation. The view catches `CancellationException` separately from other errors and reports
`"Processing cancelled"`. A flag set after the last file does not throw, so the run reports success, which is what
happened. Window close is posted with `Platform.runLater` after the task, so it follows any pending reload.

*Alternative:* check the flag on every row and roll the current file back from a snapshot — implemented first, then
dropped by the user: the rows of a file take milliseconds, so the rollback protected a step nobody can interrupt in
practice, and it cost a temp-file snapshot of every open CSV per run and a boolean result on `updatePositions`.

*Alternative:* all-or-nothing rollback — rejected by the user; needs deferred saves and snapshots of every file.

*Alternative:* return the number of committed files and report `"N of M files processed"` — rejected by the user:
the return value mixed a counter for the message with a signal of interruption.

### 5. Process works on copies of the lists
`processFiles()` passes `List.copyOf(...)` of both panes' lists. Add/Remove/Clear stay enabled during processing as
today and no longer affect the running task.

## Risks / Trade-offs

- [The rows of an open file are mutated on the background thread while its chart may repaint] → unchanged from today
  and out of scope; the window blocks interaction, and the file is reloaded afterwards.
- [MCP tools can still open or modify files while the window is open] → accepted known limitation (proposal
  Non-goals).
- [Close takes effect only between files; a run with a single large file cannot be cancelled] → the window shows
  "Cancelling..." until the file is finished; processing a file is short compared with the save it ends with.
- [Loading positions before the first file does not check the flag] → Close during that phase takes effect before the
  first data file.
