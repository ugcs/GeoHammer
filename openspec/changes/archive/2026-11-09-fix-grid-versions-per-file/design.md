## Context

`GridLayer` keeps gridding results in a `ConcurrentMap<File, GriddingResult>`, one entry per file. "Apply to all"
(`GriddingTool.applyGridding`) runs one gridding over the open files of a template and calls
`GridLayer.setResult(file, result)` for each of them with the same `GriddingResult`; "Apply" and MCP `run_gridding`
call it for one file. Nothing marks a result as built from several files, so "Apply" on one of them leaves the shared
result on the others.

`GriddingService.runGridding(files, ...)` builds every result. `GridLayer` already reaches the open files through
`Model`, and `Templates.equals` compares the templates of two files.

## Goals / Non-Goals

**Goals:**
- One replacement rule, applied in `GridLayer.setResult` for the gridding tool and MCP alike.
- Minimal code change: no new classes, no change to callers of `setResult`.

**Non-Goals:**
- Storing a shared grid once and drawing it once.
- Undo of grids: grids are not file data and stay outside `UndoModel`.
- Changes to the grid cache and its rebuild ordering.

## Decisions

### Shared flag in the result

`GriddingResult` gets a `boolean shared` component, set by `GriddingService.runGridding` to `files.size() > 1`. The
flag describes how the result was built, so it belongs to the result and needs no caller changes. "Apply to all" over a
template with a single open file gives an individual grid, which is indistinguishable from a shared grid of one file.

Alternatives:
- Identify a shared grid by the same instance held by several files: needs a store tracking instances and a wider
  `setResult` signature.
- A flag passed to `setResult` by callers: every caller has to know the rule.

### Replacement rule in `setResult`

After putting the result of a file, `setResult` removes the result of every other open file of the same template whose
`shared` flag differs from the new result:
- a shared result removes individual grids of the template;
- an individual result removes shared grids of the template.

"Apply to all" always covers every open file of a template, so a template holds at most one shared grid, and removing
shared grids of the template removes exactly the shared grid the file was part of. A file opened after "Apply to all"
and then gridded with "Apply" also removes that shared grid, as requested in #1015.

"Apply to all" calls `setResult` per file, so the first call removes the individual grids of files that get the shared
grid in the following calls. They briefly lose their grid; the extra `GridUpdatedEvent` and rebuild are harmless.

A removal uses `results.remove(file, result)`, so it does not drop a result set by a concurrent gridding in between.

### Empty result

`GriddingService.runGridding` returns `null` when there is nothing to grid or the gridding was interrupted.
`setResult` skips a `null` result, so the gridding tool and MCP keep the current grids; MCP still reports its error.

### Time-lag regrid

`GriddingTool.onSeriesUpdated` regrids a time-lagged series automatically. It regrids the selected file only, which
would now replace a shared grid with an individual one and remove it from the other files. When the current result is
shared, it regrids the open files of the template, as "Apply to all" does.

## Risks / Trade-offs

- [Applying to one file of a shared grid hides the grid of the other files, which a user may not expect] → This is
  the behaviour requested in #1015; "Apply to all" rebuilds the shared grid.
- [A rebuild of a removed file's grid already in flight can put it back into the grid cache] → Pre-existing for closed
  files; drawing checks the result, so only `getGrid` users (palette histogram, MCP grid image) may see it briefly.
- [The rule lives in `GridLayer`, which needs the Spring context and is not unit-tested] → The rule is a few lines;
  the `shared` flag is tested through `GriddingService`.
