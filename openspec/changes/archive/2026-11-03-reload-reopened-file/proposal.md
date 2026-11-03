# Proposal

## Why

Opening a file that is already open replaces it with a new copy read from disk. Unsaved edits are dropped without a
prompt, and the closed copy is never announced, so undo, grids and MCP state keep pointing at it: Undo then restores the
detached copy and nothing visible happens (#1033). This happens through the Open dialog, drag and drop, opening a folder
that contains the file, a command line argument and a double click in Finder.

## What Changes

- Opening an open file reloads it from disk: the open copy is closed the regular way, so undo, grids and other per-file
  state are dropped with it, and the fresh copy takes the place of its chart. A second copy of the file is never opened.
- A file without unsaved changes is reloaded silently.
- A file with unsaved changes chosen explicitly (Open dialog, drop of the file, command line, Finder) asks "Keep" or
  "Reload" before the file is read. "Keep" only selects its chart; "Reload" discards the edits.
- A file with unsaved changes reached through an opened folder is kept as is, with a note in the status bar.
- When the open file is edited while its reload is read, the reload is not applied and the status bar says so.

## Non-goals

- Undoing a reload, or keeping the undo history, grids, selected series and collapsed state of a reloaded file.
- Changing MCP `open_file`: it keeps selecting an open file without reloading it.
- Detecting that the file changed on disk (modification time) to skip unnecessary reloads.
- Recognizing the same file through a relative path, symbolic links or a different letter case of the path.
- Changing how scripts apply their results to a file.

## Capabilities

### New Capabilities
- `file-opening`: what happens when a file that is already open is opened again: reload by close and open,
  confirmation of unsaved changes, and recognition of the same file.

### Modified Capabilities

## Impact

- `Loader`: check for an open file after meta resolution, separate reading of a file instance from
  adding its chart, blocking Keep/Reload dialog, reload by closing the open file and adding the fresh one.
- `Model`: replacing the chart of a closed file at the same position; `initChart` no longer recreates the chart of an
  open file.
- `Dialogs`: a blocking confirmation awaited from a background thread.
