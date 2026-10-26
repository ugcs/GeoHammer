# Proposal

## Why

The geotagging window is non-modal and keeps the main window's file instances in its lists (#1034). When the user
closes a file in the main window without saving, the geotagger still holds the edited instance and, since the file is
no longer open, "Process" saves it over the source file, writing edits the user chose to discard. The reverse also
happens: a file the geotagger loaded from disk can be opened and edited in the main window, and the two copies
overwrite each other's results.

Making the window modal alone is not enough: "Close" stays enabled during processing, so the user can close the
window mid-run, which releases the main window while processing continues and reopens the same race.

## What Changes

- The geotagging window is application-modal and owned by the main window: while it is open, the main window and
  other GeoHammer windows do not accept input, so files cannot be closed, opened or edited there.
- Closing the window (Close button or window close button) while processing runs cancels processing. The window
  stays open and modal, shows that it is cancelling, and closes only after processing has stopped.
- Cancellation stops between files: the file being processed is finished (saved or reloaded) and remaining files are
  not touched. Cancellation never interrupts a file midway, so no file is left half-updated or half-written.
- The status bar reports that processing was cancelled.
- Processing works on the file lists as they were when "Process" was pressed.

## Non-goals

- A separate Cancel button; the window keeps only Close and Process to avoid two similar actions side by side.
- Keyboard shortcuts for the window, such as Esc to close it.
- Rolling back files in a cancelled run, either all of them or the file being processed; processing one file takes
  milliseconds, so stopping inside a file gains nothing.
- Guarding against MCP tools that open or modify files while the geotagging window is open; considered unrealistic
  and left as a known limitation.
- Making geotagging undoable or changing how it clears the undo history of open files.
- Atomic (temp file and rename) saving of files.

## Capabilities

### New Capabilities
- `geotagging`: the geotagging window: how it interacts with the main window and open files, and how processing runs,
  completes and is cancelled.

### Modified Capabilities

## Impact

- `GeotaggerView` (window modality and owner, close handling during processing, status messages), `Geotagger`
  (cooperative cancellation between files), `ProfileView` (toggle logic of the Geotagging button).
- No format, API or dependency changes.
