# Proposal

## Why

When a GPR (SEG-Y) or sonar (svlog) file that is not open in GeoHammer is geotagged, the geotagger saves it over the
source with its `.geohammer` sidecar applied (#1028). The source loses the traces of removed lines and the samples
outside the sample range, and background removal is baked into the samples, while the sidecar still refers to the
raw trace indices and still asks for the sample range and background removal. On reopen the sidecar maps to the wrong
traces or fails to open, and the sample range and background removal are applied a second time.

The sidecar does not store positions, so geotagging has to write the source file, but it must write only positions.

## What Changes

- For a GPR or sonar data file that is not open in GeoHammer, the geotagger no longer saves the file as it is shown;
  it writes only the new positions into the source file.
- The GPR source keeps every trace with its own samples, mark byte and other header fields; only the positions of the
  geotagged traces change.
- The sonar source keeps every packet; only the positions in the packets of the geotagged traces change.
- Traces of lines removed in GeoHammer are not geotagged and keep their old positions in the source, as today.
- Loading of files, the sidecar and files open in GeoHammer are not changed.

## Non-goals

- Changing how files are opened, including in the geotagging window.
- Geotagging traces of lines removed in GeoHammer.
- Positions of an open GPR or sonar file are lost on "Save", which writes only the sidecar; that is a separate issue.
- DZT files (not accepted by the geotagging window) and NMEA files (not a geotagging target).
- Writing altitude to the source; geotagging of GPR and sonar files writes latitude and longitude only, as today.
- Atomic (temp file and rename) saving of files.

## Capabilities

### New Capabilities

### Modified Capabilities
- `geotagging`: adds the requirement that geotagging a GPR or sonar file not open in GeoHammer changes only the
  positions in its source and keeps the source consistent with its sidecar.

## Impact

- `Geotagger` (saving of files that are not open), `GprFile` and `SonarFile` (new method that writes positions into
  the source).
- No change to file loading, file formats, APIs or dependencies.
