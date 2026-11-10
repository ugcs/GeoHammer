## Why

After "Apply to all" builds a shared grid for the files of a template, "Apply" on one of those files gives it an
individual grid while the shared grid stays on the other files and still covers the area of that file. The map then
shows either version over that file depending on which file is selected, so switching files makes the grid of a single
file alternate (#1015).

## What Changes

- A gridding result records whether it is shared, i.e. built from several files.
- "Apply to all" removes the individual grids of all files of its template; its files get the shared grid.
- "Apply" removes shared grids from all files of its template and keeps individual grids of other files.
- The MCP gridding tool builds an individual grid, so it follows the same rule as "Apply".
- The automatic regrid of a time-lagged series rebuilds a shared grid for all files of the template instead of
  replacing it with an individual grid of the selected file.
- Grids of files of other templates are never affected.
- A gridding that produces no grid (no values of the series, data extent smaller than a cell, or cancelled) keeps the
  current grids instead of failing the task.

## Non-goals

- Changing how grids of different files are drawn where they overlap spatially (the selected file stays on top).
- Storing a shared grid once instead of per file, so that it is filtered and drawn once (a separate refactor).
- Ordering of grid cache rebuilds and removals of the same file.

## Capabilities

### New Capabilities
- `gridding`: which grids are shown for open data files after "Apply", "Apply to all" and MCP gridding, and how a new
  grid replaces existing ones.

### Modified Capabilities

## Impact

- `GriddingResult` (new `shared` component), `GriddingService` (sets it), `GridLayer.setResult` (removes grids of the
  other kind from files of the same template), `GriddingTool` (time-lag regrid).
- `GridUpdatedEvent` with a null grid is published for every file that loses its grid; `GriddingTool` and
  `PaletteView` already handle a null grid.
- No persisted format, settings or dependency changes.
