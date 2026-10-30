# Proposal

## Why

Apply to all of the Low-pass, Running median and Time lag tools compares every chart with the selected file while the
task runs (#1038). When the user runs Apply to all on several large CSV files of template X and then selects a chart of
template Y, the remaining X files are skipped, and the Y files that have a series with the same name are filtered and
marked unsaved. The Quality control tool has the same race, and tool preferences and the gridding filter are also read
from the tool after the task has started, so a file switch right after the click can store them under another template.

## What Changes

- A tool action applies to the file, template, series and input values that were selected when the user started it.
  Selecting another file, series or template while the task runs does not change what the task processes.
- Apply to all processes the files of the selected template that are open when the user starts it; a file opened while
  the task runs is not processed.
- Tool preferences are saved for the template of the file selected when the action started.
- Applies to Apply and Apply to all of the Low-pass, Running median, Time lag, Quality control and Gridding tools, and
  to saving preferences for every tool that runs tasks.

## Non-goals

- Files closed while a task runs: the task still processes them, as today.
- Moving work that changes the UI out of background tasks (e.g. `qualityLayer.setIssues`).
- A shared helper for collecting the files of a template; each tool keeps its own few lines.
- Tools that already capture their target before the task starts (GPR background, GPR elevation) and script execution.
- Concurrency between MCP tools and right-panel tools.

## Capabilities

### New Capabilities

### Modified Capabilities
- `filter-tools`: adds a requirement that a running task keeps the target and inputs it was started with.

## Impact

- `FilterToolView.submitAction` (preferences saved before the task is submitted), `LowPassTool`, `RunningMedianTool`,
  `TimeLagTool`, `QualityControlTool` (`onApply`, `onApplyToAll`) and `GriddingTool.applyGridding`.
- No change to file data formats, settings keys, APIs or dependencies.
