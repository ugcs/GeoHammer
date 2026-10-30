## 1. SEG-Y 2-byte samples

- [x] 1.1 In `TC2BICodec.encode`, clamp each sample to `Short.MIN_VALUE..Short.MAX_VALUE` before the cast; verify by
  compiling
- [x] 1.2 Add `TC2BICodecTest` (package `format.gpr.segy`): encode then decode 40000, -40000, 32767, -32768, an
  in-range value and NaN in both byte orders; assert 32767, -32768, the bounds unchanged, the value truncated as
  today and 0; verify it passes

## 2. DZT 16-bit samples

- [x] 2.1 Add `writeIndex(ByteBuffer, int)` to the DZT `SampleCodec`: `Sample16Bit` writes `(short) (index + offset)`
  as `write` does today, `Sample32Bit` writes the int; switch `DztWriter.writeSamples` to `writeIndex` for the
  trace index; verify `DztFileTest` passes
- [x] 2.2 In `Sample16Bit`, name the 32767 offset and the -32767..32768 range as constants and clamp the value in
  `write` before adding the offset; verify by compiling
- [x] 2.3 Add `SampleCodecTest` (package `format.dzt`): for the 16-bit codec, write then read 40000, -40000, 32768,
  -32767 and an in-range value, asserting 32768, -32767 and the rest unchanged; `writeIndex(40000)` writes the same
  bytes as `putShort((short) (40000 + 32767))`; for the 32-bit codec, write and read back an int and an index;
  verify it passes

## 3. Verify

- [x] 3.1 Run `mvn clean package`; fix new compilation errors and Error Prone/NullAway warnings, and verify all tests
  pass
- [x] 3.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference; fix critical and major issues, and report the scores and any issues left open
