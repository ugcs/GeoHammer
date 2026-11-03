# Proposal

## Why

A tool in the right panel disables its Apply buttons while its task runs, but input validation re-enables them before
the task completes (#1039). Selecting a file of another template or another series re-validates the input, so a second
filter or grid can start while the first is still running: two filters then change the same rows at once, and an older
grid can finish last and replace the newer one. When the task completes, the buttons are enabled even if the current
input is invalid.

The automatic regrid after a time lag has the same race without any button: it starts a new grid even while one is
still being computed.

## What Changes

- Apply and Apply to all of a tool stay disabled while the tool's task runs, whatever the selected file, series or
  input. When the task completes, they are enabled only if the current input is valid.
- The tool's inputs stay disabled while its task runs; selecting another file no longer re-enables them.
- When a time-lagged series is updated while the gridding tool is computing a grid, the grid is not recomputed
  automatically; the "Grid needs to be recalculated" warning is shown instead.
- Applies to the Low-pass, Running median, Time lag, Quality control, Gridding, GPR background and GPR elevation tools.

## Non-goals

- Running tasks of different tools at the same time (e.g. a low-pass and a running median on the same file).
- Concurrency between MCP tools and tools in the right panel.
- Tasks that read the selected file or inputs after they started, so a file switch during the task affects them; that
  is a separate issue.
- Script execution, which tracks running scripts per file and keeps doing so.
- Queueing the automatic regrid to run after the current grid completes.

## Capabilities

### New Capabilities
- `filter-tools`: running tasks of right-panel processing tools: availability of their actions and inputs while a task
  runs, and the automatic regrid of a time-lagged series.

### Modified Capabilities

## Impact

- `FilterToolView` (running state of the tool, availability of actions and inputs), its subclasses that validate input
  (`LowPassTool`, `RunningMedianTool`, `TimeLagTool`, `QualityControlTool`, `GriddingTool`), `GprElevationTool` (input
  availability) and `GriddingTool` (automatic regrid).
- No change to file data, file formats, APIs or dependencies.
