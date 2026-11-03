# Tasks

## 1. Scaled channel headers

- [x] 1.1 In `DztWriter.writeHeaders`, copy each channel's header buffer into a new little-endian
  `DztHeader.MINHEADSIZE` buffer and write the copy instead of patching `header.getBuffer()`; verify the existing
  `DztFileTest` tests still pass
- [x] 1.2 In the copy, put `rh_nsamp = numSamples + 1` and, when the parsed source `rh_nsamp` is positive,
  `rhf_range` and `rhf_depth` scaled by `(numSamples + 1) / rh_nsamp` from the parsed source fields (offsets 26 and
  62 as named constants next to `RH_NSAMP_OFFSET`); otherwise keep the source values; verify by tasks 2.2–2.5

## 2. Tests

- [x] 2.1 Extend the synthetic DZT in `DztFileTest.writeDzt` with a time range and a depth range per channel, with
  different values for the two channels; verify the open test reports them in each channel header
- [x] 2.2 Test sample crop to a sub-range starting below the first sample, `save(file)`: in the reopened file each
  channel's `rhf_range / rh_nsamp` and `rhf_depth / rh_nsamp` equal the source channel's within float tolerance, and
  `getSampleIntervalNanos()` equals the source's; verify it fails on the old code and passes with section 1
- [x] 2.3 Test `save(file, range)` with a line sub-range of a sample-cropped file (the separate-lines path) with the
  same checks; verify it passes
- [x] 2.4 Test save without a crop: saved `rh_nsamp`, `rhf_range` and `rhf_depth` equal the source values; verify it
  passes
- [x] 2.5 Test that saving a sample-cropped file twice writes identical headers and leaves the source's header
  buffers unchanged; verify it fails on the old code for the buffer check and passes with task 1.1
- [x] 2.6 Run `mvn test` and verify no regressions

## 3. Verify

- [x] 3.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 3.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
  uncommitted changes with this change's proposal and specs as the completeness reference, fix critical and major
  issues, and report the scores and any issues left open
