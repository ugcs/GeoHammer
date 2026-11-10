# Proposal

## Why

The reload of an already open file (#1033) is split between `Model.initChart` and a separate `Model.replaceFile` with
a version check, and closing a file is done by a `FileClosedEvent` listener in `ProfileView`, so the event works as a
command and a replace triggers a second close of the same file. Review asked to keep the model API small: replace an
open file inside `initChart`, let `Model` close files and publish `FileClosedEvent` only as a notification. The version
check against edits made while the reload is read is dropped to keep the reload path simple.

## What Changes

- A reload is no longer skipped when the open file is edited while its reload is read: the reload is applied and those
  edits are lost, as edits made before choosing "Reload".
- Opening, reloading and closing keep their current user-visible behavior otherwise: the Keep/Reload confirmation,
  silent reload of unmodified files, kept files from folders and the chart position are unchanged.

## Non-goals

- Changing the Keep/Reload confirmation, folder handling or MCP `open_file`.
- Undoing a reload or keeping per-file state of a reloaded file.
- Detecting changes of the file on disk.

## Capabilities

### New Capabilities

### Modified Capabilities
- `file-opening`: the requirement that a reload does not overwrite concurrent edits is removed.

## Impact

- `Model`: `initChart` replaces the chart of an open file in place; new `closeFile` removes the file and publishes
  `FileClosedEvent`, `justdraw` and `traceValues`; `removeChart` updates aux elements and selects a file that still has
  a chart; `replaceFile` is removed.
- `Loader`: a reload is applied through `initChart` without a version check.
- `Chart.close`, `Saver.onCloseAll`: close through `removeChart` and `closeFile` instead of publishing the event.
- `ProfileView`: its `FileClosedEvent` listener only resets its own state.
- `spec/overview.md`: Reopening and Events sections.
