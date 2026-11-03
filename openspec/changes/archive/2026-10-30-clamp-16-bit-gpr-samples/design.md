## Context

GPR samples are floats in memory (`Trace`). Two encoders write them as 16-bit integers:

```
SEG-Y  GprFile.save -> TC2BICodec.encode     putShort((short) sample)
DZT    DztFile.save -> DztWriter.writeSamples
                         codec.write(buffer, index)              <-- trace-index word
                         codec.write(buffer, (int) sample) x N   <-- samples
                       Sample16Bit.write     putShort((short) (value + 32767))
```

A float-to-short cast goes through int: the float-to-int step saturates (NaN becomes 0), the int-to-short step keeps
the low 16 bits, so out-of-range values wrap. The 32-bit encoders (`TC4BICodec`, `Sample32Bit` after `(int) sample`)
stop at the float-to-int step and already saturate.

`Sample16Bit.read` returns `(u16 & 0xFFFF) - 32767`, so the values a 16-bit DZT sample can hold are -32767..32768.
The reader skips the trace-index word with a plain `read`. Samples are written to disk only by `save(File)` and
`save(File, IndexRange)` ("Save to.." and script copies); plain "Save" of a GPR file writes meta only.

## Goals / Non-Goals

**Goals:**
- Each 16-bit codec owns its representable range and clamps samples to it.

**Non-Goals:**
- Changing the `SampleCodec` contract of SEG-Y codecs or any reader.
- Sharing a clamp helper between the SEG-Y and DZT codecs: two one-line clamps beat a shared abstraction.

## Decisions

### The codec clamps, not the writer
`TC2BICodec.encode` clamps each float sample to `Short.MIN_VALUE..Short.MAX_VALUE` before the cast. `Sample16Bit.write`
clamps the int value to -32767..32768 before adding the offset. The range follows from each codec's encoding, so it
stays next to it.

Alternative: the DZT codec exposes `minValue()`/`maxValue()` and `DztWriter` clamps. Rejected: the writer would need to
know each bit depth's offset range, and every future caller would have to remember to clamp.

### Separate trace-index write in the DZT codec
The DZT `SampleCodec` gets `writeIndex(ByteBuffer, int)` next to `write`. `Sample16Bit.writeIndex` keeps today's encoding
(`(short) (index + 32767)`, wrapping past 32768 traces); `Sample32Bit.writeIndex` writes the int. `DztWriter.writeSamples`
writes the index through `writeIndex`. `write` then means "write a sample value" and can clamp without affecting the
index.

Alternative: clamp everything in `write`. Rejected: trace indices above 32768 on 16-bit files would all become 32768,
which other GSSI tools may read; that is outside this fix.

### Clamp before the cast, keep truncation
Clamping first and casting after keeps the current truncation toward zero for in-range values and NaN handling
(`Math.clamp` returns NaN, the cast writes 0). In `Sample16Bit`, the existing 32767 offset and the new range bounds
become named constants.

### Data, threads and events
No file data changes: the open file's samples are untouched, only the bytes written are limited. No snapshot, undo,
`setUnsaved`, cache invalidation or events are involved. Saving keeps running on the thread it runs on today.

## Risks / Trade-offs

- [Clamping loses the peak amplitude of saturated samples] → Expected and far less harmful than a sign flip; users
  who need full range can save to a 32-bit or float format outside this change.
- [Users are not told samples were clamped] → Accepted (proposal non-goal); the saved data is visibly saturated rather
  than inverted.
