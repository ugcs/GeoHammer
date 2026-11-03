# Proposal

## Why

The GPR elevation tool reads its controls (header selectors, trace lag, sample offset, peak detection, filter window
and the "Remove air gap" flag) inside tasks running on the executor (#1023). JavaFX controls are not thread-safe, so a
task can apply stale or inconsistent values, especially while the user is editing. When another GPR file is selected,
the tool also rebuilds that file's ground surface once per control it resets; once the values are read at the moment
of the change, those rebuilds would apply a mix of the new file's and the previous file's settings to the new file.
While the surface is rebuilt, the GPR chart can also draw it half-built (zeros, raw altitudes or unfiltered peaks), so
the profile line jitters while the user drags an elevation slider.
The other locations in #1023 (`FilterToolView.submitAction`, `QualityControlTool`) were already fixed by #1038.

## What Changes

- Editing a GPR elevation input rebuilds the ground surface with the input values as they were at the moment of the
  edit.
- Flatten surface uses the "Remove air gap" value that was set when the user started it.
- Selecting another GPR file shows that file's ground profile settings without changing or rebuilding its ground
  profile.
- The GPR chart shows only complete ground surfaces: while a surface is rebuilt, the previous one stays on the chart.

## Non-goals

- Pushing the flatten surface undo frame on the FX thread; it stays in the task as today.
- Making `HorizontalProfile` settings safe for concurrent access (a profile update running while the tool view reads
  the profile settings, or while surface flattening runs); only publishing the surface is addressed.
- The ellipsoidal height selector, which has no change listener; its value is applied on the next change of another
  input, as today.
- Other tools: their tasks no longer read controls after #1038.

## Capabilities

### New Capabilities

### Modified Capabilities
- `filter-tools`: adds GPR elevation scenarios to the requirement that a task keeps the target and inputs it was
  started with, and requirements that selecting a GPR file does not change its ground profile and that the chart shows
  only complete ground surfaces.

## Impact

- `GprElevationTool` (`updateProfile`, `flattenSurface`, `updateView`) and `HorizontalProfile.buildSurface`.
- No change to file data formats, settings keys, APIs or dependencies.
