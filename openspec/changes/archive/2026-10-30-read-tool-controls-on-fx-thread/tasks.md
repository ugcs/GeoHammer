## 1. GPR elevation tool

- [x] 1.1 In `GprElevationTool.updateProfile`, read the header selectors, sliders and peak detection into a private
  `ProfileParams` record on the FX thread before `pendingExecutor.submit`, and use only the record in the task; verify
  no control is referenced inside the submitted lambda
- [x] 1.2 In `GprElevationTool.flattenSurface`, read `removeAirGap.isSelected()` into a local before `submitAction`
  and pass it to `flattenSurface`; verify the task body references no control
- [x] 1.3 Add a `ReentranceGuard` field wrapping the body of `GprElevationTool.updateView`, and return early from
  `updateProfile` while it is latched; verify `updateProfile` is the only reader of the guard and `updateView` runs
  its resets inside it
- [x] 1.4 In `HorizontalProfile.buildSurface`, build the surface in a local array and assign the `surface` field
  (made `volatile`) only after peak detection and median filtering; verify no step writes to the field before the
  final assignment, and that `getSurfaceIndex` and `flattenSurface` read the field once per call

## 2. Verify

- [x] 2.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 2.2 Invoke the `code-review` skill (not the `code-review:code-review` plugin) on the uncommitted changes with this
  change's proposal and specs as the completeness reference, fix critical and major issues, and report the scores and
  any issues left open
