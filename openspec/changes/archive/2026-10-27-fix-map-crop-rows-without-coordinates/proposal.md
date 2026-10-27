# Proposal

## Why

Cropping lines on the map crashes when an open file has points without coordinates, and the crash leaves the file
corrupted: some points are already shifted and duplicated, marks are offset and line numbers rewritten, while the chart
is not reloaded and the file is not marked as unsaved (#1031). Such points are common: GPR files without GPS, files the
geotagger covered only partly, and sonar or NMEA files with gaps in positions.

## What Changes

- Points without coordinates are treated as outside the crop area and are removed by the crop, consistent with the map,
  where they are not shown.
- A file with no points with coordinates inside the crop area stays unchanged, as before.
- Which points are inside is decided before the file is modified, so a crop either completes or leaves the file
  unchanged.
- The same applies to cropping by region through MCP, which uses the same operation.

## Non-goals

- Keeping points without coordinates or interpolating positions for them during the crop.
- Changing how cropped points are split into lines or how marks and selection follow the kept points.
- Merging map cropping with cropping to index ranges into one shared implementation.

## Capabilities

### New Capabilities

### Modified Capabilities
- `map-line-cropping`: adds the rule for which points of a file the crop keeps, including points without coordinates.

## Impact

- `TraceTransform` (map crop), used by `TraceCutter` and the MCP tool `CropByRegion`.
- New unit tests for `TraceTransform`.
