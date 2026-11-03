## Why

Saving a GPR file with 16-bit integer samples corrupts samples outside the 16-bit range (#1036). Gain or background
removal can push a sample past the range, and the writer wraps it instead of limiting it: a sample of 40000 is saved as
-25536. Strong reflections then show up in the saved file as strong values of the opposite sign.

## What Changes

- When a GPR file stores 16-bit integer samples (SEG-Y two's complement 2-byte, DZT 16-bit), each sample is clamped to
  the range the format can hold, instead of wrapping.
- SEG-Y 2-byte samples are limited to -32768..32767; DZT 16-bit samples to -32767..32768, the range its offset encoding
  holds as GeoHammer reads it back.
- The trace-index word that starts each DZT trace is written as it is today.
- The fix applies to every path that writes GPR samples: "Save to.." (single file and separate lines) and copies made
  for script execution.

## Non-goals

- Changing how the DZT trace-index word is written, including its wrap past 32768 traces on 16-bit files.
- Rounding samples to the nearest integer: samples are still truncated toward zero as today.
- Changing the DZT 16-bit zero offset (32767).
- 32-bit integer and floating-point sample formats: they already keep values within their range.
- Warning the user that samples were clamped.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `file-saving`: saved GPR files with 16-bit integer samples keep out-of-range samples at the nearest representable
  value with their sign.

## Impact

- `format/gpr/segy`: `TC2BICodec.encode` clamps samples.
- `format/dzt`: `SampleCodec` separates sample writes (clamped by `Sample16Bit`) from the trace-index write;
  `DztWriter.writeSamples` writes the index through the new method.
- New unit tests for both codecs.
- No changes to reading, meta, the open file, events or dependencies.
