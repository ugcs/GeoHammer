# Proposal

## Why

"Save to.." on a SEG-Y GPR file writes a running mark number into the mark byte of each marked trace header. The byte
overflows, so the 256th, 512th, … marks are written as 0, which reads back as "not marked": in files with 256 or more
marks, those marks are silently lost after save and reopen (#1025). Line boundary marks added on export make such
counts reachable on long surveys.

## What Changes

- Every marked trace saved to a SEG-Y file gets the mark value 1; unmarked traces get 0. Running mark numbers are no
  longer written.
- All marks of the saved traces survive a save and reopen, regardless of their count, for both "Single file" and
  "Separate lines".

## Non-goals

- Keeping the original mark values of the source file (for example numbers written by the GPR device).
- Changing how marks are read on load, how they are stored in meta, or how "Save" stores them.
- Changing mark handling of DZT and other formats.

## Capabilities

### New Capabilities

### Modified Capabilities
- `file-saving`: adds the rule that "Save to.." on a GPR file keeps every mark of the saved traces.

## Impact

- `GprFile.save(File, IndexRange)`, used by `Saver` for "Save to.." → "Single file" and "Separate lines".
- New unit tests for saving and reopening a `GprFile` with marks.
