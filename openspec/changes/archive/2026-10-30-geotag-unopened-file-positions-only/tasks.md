# Tasks

## 1. GPR positions written into the source

- [x] 1.1 In `GprFile`, move the latitude/longitude writing of `updateTraceBuffer` into a private
  `writePosition(Trace, ByteBuffer)` used by `updateTraceBuffer`; verify `GprFileTest` stays green
- [x] 1.2 Add `GprFile.writePositionsToSource()`: `BinFile.load(getFile())`, fail with `IOException` when its trace
  count differs from the raw traces, write the position of every meta value's raw trace into the header of the same
  trace with `writePosition`, `binFile.save(getFile())`; verify by the tests from 1.3
- [x] 1.3 Add `GprFileTest` tests on the SEG-Y fixture copied to a temp dir: `open` it, remove the first line, crop
  the sample range, remove background, add marks, `saveMeta`; `open` it again as the geotagging window does, set new
  positions on every `getGeoData()` value, `copyPositionsFromMeta`, `writePositionsToSource`; assert the source has
  the original trace count and sample bytes, the removed traces have unchanged headers, the shown traces have the new
  positions, and a regular `open` shows the same lines, the cropped sample range once, background removed once and
  the same marks; add a test that a source with a different trace count fails with `IOException`; verify the tests
  pass

## 2. Sonar positions written into the source

- [x] 2.1 Add `SonarFile.writePositionsToSource()` that writes every raw packet to `getFile()` in order; verify by the
  test from 2.2
- [x] 2.2 Add `SonarFileTest`: write a small svlog to a temp dir with `SvlogWriter` (NMEA RMC packets with position
  and time), `open` it, remove the first line, `saveMeta`; `open` it again, set new positions on every `getGeoData()`
  value, `copyPositionsFromMeta`, `writePositionsToSource`; assert the source has every packet, the packets of the
  removed line keep their positions, the shown packets have the new positions, and a regular `open` shows the first
  line removed; verify the test passes

## 3. Geotagger

- [x] 3.1 In `Geotagger.save`, call `writePositionsToSource()` for `GprFile` and `SonarFile` and keep `save(file)`
  for other files (`switch` with type patterns); verify `mvn test` stays green
- [x] 3.2 Update the Meta entry in `spec/overview.md`: the geotagger writes positions into the source of a file that
  is not open, without applying the sidecar; verify the text matches design.md

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 4.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
  uncommitted changes with this change's proposal and specs as the completeness reference, fix critical and major
  issues, and report the scores and any issues left open
