# Tasks

## 1. Crop treats points without coordinates as outside

- [x] 1.1 In `TraceTransform`, make the crop area check null-safe: a point whose `GeoData.getLatLon()` is null is
  outside the area; verify by the build in 2.1 and the review in 2.2
- [x] 1.2 In `TraceTransform.cropLines(file, ...)`, decide for every point whether it is inside before the first
  mutation of rows, marks or selection, then apply the crop from that result; line splitting, mark offsets and
  selection handling stay as they are; verify by the build in 2.1 and the review in 2.2

## 2. Verify

- [x] 2.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 2.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
  uncommitted changes with this change's proposal and specs as the completeness reference, fix critical and major
  issues, and report the scores and any issues left open
