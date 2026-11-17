## Why

The size of a grid depends only on the extent of the data and the cell size, and nothing limits it. One row with a
position far from the survey (a GPS dropout at latitude and longitude 0), or a very small cell size, asks for
billions of cells: the app runs out of memory or freezes, other open files are at risk, and the user gets no message
(#1040).

## What Changes

- Gridding is rejected when the grid would hold more cells than an internal limit of 50 million, which can change
  in a later version. The rejection is decided before the grid is built, so it is immediate and costs no memory.
- The rejection has one simple message; its wording is not part of the requirements.
- The cell size and blanking distance typed by the user stay in the inputs after a rejection. They used to be reset
  to the parameters of the current grid whenever a gridding produced no grid.
- A tool of the right panel shows the message of a failed action in its warning banner. Until now a failed action
  was only written to the log. The gridding tool shows the rejection this way.
- The banner with an error is not hidden after a few seconds. It is hidden when the next action of the tool succeeds;
  in the gridding tool also when the cell size, blanking distance, file or series change.
- The MCP gridding tool returns the same message as a tool error, and its description states the limit.
- Existing grids are kept when gridding is rejected.

## Non-goals

- Coarsening the cell size automatically to fit the limit, or suggesting a cell size that fits.
- Counting the padding of the blanking mask against the limit (see design.md, Risks).
- Finding, skipping or flagging rows with invalid coordinates.
- Making a running gridding cancellable or faster.
- Changing the range of the cell size and blanking distance accepted by the gridding tool or by the MCP tool.
- A setting for the limit, or a limit that depends on the memory of the machine.
- Limiting the grid of the radargram projection tool, which already has its own limit.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gridding`: a new requirement on the largest grid that is built and on how a larger one is rejected.
- `filter-tools`: a new requirement that a tool shows the message of a failed action in its warning banner.

## Impact

- `service/gridding/GriddingService.runGridding`: a check of the number of cells after the envelope is padded.
- `chart/tool/FilterToolView.submitAction`: shows the message of a failed action in the warning banner; this applies
  to every tool that extends it (gridding, low-pass, running median, time lag, quality control, GPR background and
  elevation).
- `chart/tool/GriddingTool`: updates its banner when another file is selected, and publishes the display filter
  together with the new grid instead of before the gridding.
- `mcp/tool/RunGridding`: description text only; the rejection reaches the agent through the existing error path.
- A gridding that works today on more than 50 million cells is rejected after this change. Such a grid takes several
  minutes and gigabytes of memory to build (see design.md).
- `spec/overview.md`: the Grid entry of the glossary.
