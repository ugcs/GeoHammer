# Proposal

## Why

The depth scale of a DZT file is wrong after its sample range is cropped and the file is saved with "Save to.."
(#1037). The writer updates the samples per scan in each channel header but keeps the recorded time range and depth
range of the source. GeoHammer takes the time of one sample as the time range divided by the samples per scan, so
when the saved file is reopened, every time and depth is off by the crop ratio. For example, cropping 512 samples to
256 roughly doubles all depths.

## What Changes

- "Save to.." on a DZT file (single file or separate lines) writes a time range and a depth range scaled with the
  samples per scan in each channel header. A reopened file then has the same time per sample as the source.
- Saving does not change the headers of the open file: saving the same file again gives the same headers.

## Non-goals

- Shifting the time position (`rhf_position`) or the depth position (`rhf_top`) when the top of the sample range is
  cropped. GeoHammer does not read them, and the shift direction other tools expect is not confirmed; left for later.
- Updating the header checksum, which saving does not maintain today either.
- Changing "Save" (the source DZT file is not rewritten) or "Save to.." for SEG-Y, sonar, NMEA or CSV files.
- Shifting the SEG-Y trace delay when the top of the sample range is cropped.

## Capabilities

### New Capabilities

### Modified Capabilities
- `file-saving`: adds that "Save to.." on a DZT file keeps the time per sample and the depth per sample of each
  channel after a sample crop.

## Impact

- `DztWriter` (channel headers written on save).
- Callers are unchanged: `DztFile.save`, used by `Saver` ("Save to.." single file and separate lines) and by script
  line export, which now passes scripts a DZT with a correct time scale too.
- No format, API or dependency changes. Files saved before the fix keep their wrong headers.
