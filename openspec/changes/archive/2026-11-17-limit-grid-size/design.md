## Context

See proposal.md for the motivation. What shapes the approach:

- `GriddingService.runGridding` is the only place a grid is built. Its callers are `GriddingTool.applyGridding`
  (Apply, Apply to all, the time-lag rebuild in `onSeriesUpdated`) and `RunGridding.invoke` (MCP). Both run it on a
  background virtual thread.
- The grid size is `(int) (extent / cellSize)` per axis, after the envelope is padded by two cells. The cast
  saturates on a huge extent and `new float[width][height]` then fails with `OutOfMemoryError`.
- A failed action of a tool is only logged: `FilterToolView.submitAction` logs and rethrows, `TaskService` logs the
  failed task. `McpTools.callTool` returns the message of an `IllegalArgumentException` as a tool error without
  logging.
- The warning banner of `FilterToolView` (`showWarning(message, autoHide)`, `hideWarning()`) is one label. In
  `GriddingTool` it is shared with "Grid needs to be recalculated", set or hidden in `onInputChange` (cell size,
  blanking distance, series selection), at the start of a gridding (before this change), and on data changes of a
  file with a grid.

Measured on synthetic square grids with the service settings (200 iterations, tension 0.9999, a survey line every 5
cells; 8 cores):

| Cells | Spline solve | Peak heap (upper bound) |
|-------|--------------|-------------------------|
| 1M    | 0.9 s        | 47 MB                   |
| 4M    | 12.3 s       | 199 MB                  |
| 16M   | 47.9 s       | 572 MB                  |

The solve is linear in the cell count, about 3 s per million cells, and is not interruptible. By the allocations, a
grid costs about 30 bytes per cell while it is solved (grid, mask and six work arrays of `SplinesGridder2`) and 8-12
bytes per cell per file afterwards.

## Goals / Non-Goals

**Goals:**

- The smallest change that turns the crash into a message: one check, one message, no new types.
- Error display that every tool of the right panel gets for free.

**Non-Goals:**

- A message that tells which cell size fits or what the data extent is.
- Bounding the padded array of `GridMask`.
- Validating the range of the MCP parameters: the limit already rejects every oversized grid.

## Decisions

### The limit is 50 million cells

By the measurement this is about 2.5 minutes of solving and 1.5-2.5 GB at the peak (extrapolated), and 0.4-0.6 GB
kept per gridded file. It allows a cell size of 0.1 m on about 700 m by 700 m and a cell size of 1 m on 7 km by
7 km.

Alternatives: 25M (shorter worst wait, rejects more dense surveys), 100M (5 minutes of an uncancellable task), 10M
as in the projection `Grid.MAX_CELLS`. A limit derived from the heap was dropped: the packaged app sets
`-Xmx16384m` on any machine, and a fixed number gives the same answer on every machine and to every MCP agent.

### One check in `runGridding`

After the envelope is padded and the grid width and height are computed, their product is checked as a `long`
before anything is allocated; reading the data points is the only work done. The casts to `int` saturate on a huge
extent and the product of two such values still fits a `long`. Above `MAX_GRID_CELLS` the service throws an
`IllegalArgumentException` with a short message that asks to increase the cell size. The limit is an internal
constant that can change, so the message, the tests and the specs do not depend on its wording. `runGridding` keeps
returning null for "no grid".

An earlier version of this change had a `GridSize` record, a `GridSizeException`, the blanking padding in the count
and a computed smallest cell size in the message. It was dropped as more than the problem needs.

### `FilterToolView.submitAction` shows the failure

In the `catch` of the submitted action (background thread), after the existing `log.error`, the message of the
exception is posted to the FX thread with `showWarning(message, false)`; an exception without a message shows
"Action failed". An `InterruptedException` or an interrupted thread shows nothing, as it is a cancelled task. The
exception is rethrown as before, so `TaskService` still sees a failed task.

The banner is not hidden when an action starts: hiding it and showing it again on a failure makes the content of
the tool jump. `showWarning` and `hideWarning` increment a `warningVersion` counter (FX thread only; hiding a
banner that is already hidden changes nothing). `submitAction`
remembers the counter when the action is submitted and, when the action succeeds, hides the banner on the FX thread
only if the counter is unchanged. A warning shown while the action runs (a data change during a gridding) changes
the counter and is kept, as it was when the banner was hidden at the start. `GriddingTool.applyGridding` no longer
hides the recalculation warning at the start of its action; the shared handler hides it on success.

This covers every tool that extends `FilterToolView`; `GriddingTool` needs no code for the rejection itself.

- MCP: `McpTools.callTool` already turns an `IllegalArgumentException` into a tool error. `RunGridding` gets only a
  description update.

Alternative: catching the exception in `GriddingTool.applyGridding` only. Dropped in favour of the general handler.

### `GriddingTool` updates the banner on a file change

The inputs fire `onInputChange` only when their text changes, and files of one template usually load the same
parameters, so the banner of the previous file would stay. `GriddingTool` overrides `updateView()` (called by
`ToolView.selectFile` on the FX thread) to call `onInputChange()`. This also clears a recalculation warning left
over a newly selected file with the same parameters. Cell size, blanking distance and series changes already go
through `onInputChange`.

### The display filter is published with the new grid

`applyGridding` called `publishFilter()` before the gridding. `GridLayer.setFilter` updates the current grid and
publishes a `GridUpdatedEvent`, and `GriddingTool.onGridUpdated` then sets the inputs to the parameters of that grid.
A successful gridding hides this, as the new grid has the typed parameters; a rejected one left the inputs reset.

The filter and its series are now read from the controls on the FX thread before the action is submitted, and
`gridLayer.setFilter` is called inside the action (background thread, as `RunGridding` does) only when the gridding
returned a result, right before `setResult`. A gridding without a result publishes nothing, so no event is fired
and the inputs keep the typed values.

Alternative: skip the input sync in `onGridUpdated` while the tool is running. Dropped: the event arrives on another
thread and can be handled after a fast rejection has already finished the action.

## Risks / Trade-offs

- [The padded mask of `GridMask` is not counted] -> A long narrow grid with a blanking distance of thousands of
  cells can still allocate far more than the grid, for example 10 by 1,000,000 cells with a blanking radius of
  10,000 cells. It needs unusual parameters; counting the padding or reworking `GridMask` is left for a separate
  change if it shows up.
- [The banner shows the message of any exception of any tool] -> Internal messages can reach the user. They were
  invisible before, which hid real failures; messages of expected failures should be written for the user.
- [A rejected gridding is logged twice with a stack trace: as an error by `FilterToolView` and as a failed task by
  `TaskService`] -> The other tools are not registered as tasks, so the log in `FilterToolView` is their only one;
  it is kept as it was.
- [A gridding of more than 50M cells that works today is rejected] -> Such a grid takes minutes and is finer than
  the screen shows.
- [A file with one invalid position cannot be gridded, and the message does not say why] -> Today it crashes.
  Filtering such rows is a separate change.
- [The timing was measured on synthetic square grids on one machine] -> The limit bounds the grid memory on any
  machine; the wait on a slower one is longer than stated.
