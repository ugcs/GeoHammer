## Context

`GprElevationTool` edits the ground profile (`HorizontalProfile`) of the selected `TraceFile`. Every control change
(trace and altitude header selectors, trace lag, sample offset, peak detection, peak window, filter window) calls
`updateProfile` on the FX thread, which submits a task to its `SinglePendingExecutor`. The task reads all controls,
writes them to the profile and the `PositionFile`, rebuilds the surface and publishes `WhatChanged(traceValues)`.
`flattenSurface` submits a task through `FilterToolView.submitAction` that reads `removeAirGap.isSelected()`.

`updateView` runs on the FX thread from `ToolView.selectFile` (on `FileSelectedEvent`), from `WhatChanged` with
`updateButtons` or `traceCut`, and after flattening. It writes the profile's values into the controls one by one:

```
 updateView(file B)
   initTraceHeaderSelector / initAltitudeHeaderSelector   on-action detached, no update
   traceOffsetSlider.reset(B)      -> setMin/setMax/setValue -> onValue -> updateProfile
   sampleOffsetSlider.reset(B)     -> ... -> updateProfile
   detectPeaks.setSelected(B)      -> listener -> updateProfile
   peakWindowSlider.reset(B)       -> ... -> updateProfile
   surfaceFilterWindowSlider.reset -> ... -> updateProfile
```

Each reset fires only when the value changes. Today the task reads the controls late, so it usually sees the finished
reset; reading them at the moment of the change would apply a mix of B's and the previous file's values to B.

`HorizontalProfile.buildSurface` assigns a new zero-filled `surface` array first and then fills it in place: altitudes,
then `detectPeaks`, then `medianFilter`. `GPRChart.drawHorizontalProfile` reads it on the FX thread through
`getSurfaceIndex`, once per trace, on repaints triggered by `WhatChanged(traceValues)` (`Model.onChange`). While a
slider is dragged the next rebuild is usually running during that repaint, so the chart draws a partly built surface.

The profile's surface is built when the position file is loaded (`TraceFile.loadPositionFile`) and on every edit, so
no path relies on the rebuild triggered by `updateView`.

## Goals / Non-Goals

**Goals:**
- No task of `GprElevationTool` reads a JavaFX control; values are read on the FX thread and passed in as immutable
  values.
- Programmatic control updates in `updateView` never trigger `updateProfile`.
- Readers of the ground surface see only complete surfaces.

**Non-Goals:**
- Changing `ExpandableSlider` to tell user changes from programmatic resets: it is shared by other views.
- Synchronizing access to `HorizontalProfile` settings and `PositionFile` headers between the profile task,
  `updateView` and flattening.
- Making `drawHorizontalProfile` read one array per frame: a frame can still mix two complete surfaces, and the next
  repaint draws the newest one.

## Decisions

### Read profile inputs into a record before submitting
`updateProfile` keeps its guard clauses, then builds a private record `ProfileParams` (trace header, altitude header,
ellipsoidal height header, trace offset, sample offset, detect peaks, peak window, surface filter window) from the
controls on the FX thread, clamping the windows with `Math.max(0, ...)` as today. The task uses only the record, the
captured `traceFile`, `profile` and `positionFile`. Header comparison and profile writes in the task stay as they are.

The record follows `QualityControlParams` and `GriddingParams`. Alternative: capture eight locals in the method.
Rejected: the record groups one set of inputs and keeps the task body readable.

### Capture "Remove air gap" in `flattenSurface`
`flattenSurface` reads `removeAirGap.isSelected()` into a local before `submitAction`. The undo snapshot, file data
changes (`HorizontalProfile.flattenSurface` calls `tracesChanged()` and `setUnsaved(true)`), chart reload and
`FilterAppliedEvent` / `WhatChanged(traceValues)` stay unchanged.

### Guard `updateView` against profile updates
A `ReentranceGuard viewUpdateGuard` field wraps the body of `updateView` (`viewUpdateGuard.run(...)`), and
`updateProfile` returns early when `viewUpdateGuard.isLatched()`. Both run on the FX thread. The pattern is already used
by `ScriptExecutionTool` and `SeparatorsView`. User actions after `updateView` returns, including a slider's reset
button and the range expansion on mouse release, still update the profile.

Alternative: detach listeners during `updateView`, as the header selectors already do with `setOnAction(null)`.
Rejected: slider handlers are overridden `onValue` methods that cannot be detached, and the checkbox listener would
need to be kept in a field to remove and re-add it.

Alternative: skip the task when the captured values equal the profile's. Rejected: intermediate resets still differ
from the profile and would apply mixed values.

### Publish the ground surface once it is built
`buildSurface` builds into a local array (`detectPeaks` and `medianFilter` already take the array as a parameter) and
assigns `surface` once at the end. The field becomes `volatile` so the FX thread sees the complete array after the
assignment. `getSurfaceIndex` and `flattenSurface` read the field into a local once, so their bounds checks and reads
use the same array; flattening started while a rebuild finishes uses one complete surface throughout.
File data is not changed: the surface is derived view state, not saved and not part of `setUnsaved`.

Alternative: copy the surface in `drawHorizontalProfile` or lock around the build. Rejected: copying does not help
when the field already holds a partly filled array, and locking would block repaints for the build duration.

## Risks / Trade-offs

- [Selecting a file or cropping no longer rebuilds the surface when its control values differ from the previous
  file's] → the profile values and surface of the selected file were built from the same values already; the rebuild
  only repeated work. A crop changing the trace count does not rebuild the surface today either when the values are
  unchanged.
- [`ReentranceGuard.run` skips the action when already latched] → `updateView` does not call itself, so it never
  skips.
- [The flatten undo frame is pushed from the executor] → unchanged by this change; listed as a proposal non-goal.
