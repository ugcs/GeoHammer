# Design

## Context

Each `DztChannel` owns a `DztHeader`: the raw 1024-byte header buffer read from the source file plus fields parsed
from it once on open (`rh_nsamp`, `rhf_range`, `rhf_depth`, ...). The parsed fields are never changed afterwards.
`rh_nsamp` counts the samples of a scan including the leading trace index value, so a channel has `rh_nsamp - 1`
data samples. `DztFile.getSampleIntervalNanos()` is `rhf_range / rh_nsamp` of the selected channel (see the
`gpr-time-scale` spec).

On save, `DztWriter.writeHeaders` patches `rh_nsamp` to `numSamples + 1` (the first trace's `numSamples()`, already
limited to the meta sample range) directly in `header.getBuffer()` and writes that buffer. `rhf_range` and
`rhf_depth` keep the source values:

```
source           rh_nsamp = 512   rhf_range = 50 ns   dt = 50 / 512 = 0.0977 ns
saved, today     rh_nsamp = 257   rhf_range = 50 ns   dt = 50 / 257 = 0.1946 ns   (x1.99)
saved, fixed     rh_nsamp = 257   rhf_range = 25.1 ns dt = 25.1 / 257 = 0.0977 ns
```

`DztChannel.copy()` shares the header, and `Saver` saves a `copy()` of the file, so today every save also mutates the
header buffer of the open file. It is harmless only because the parsed fields stay untouched and `rh_nsamp` is
overwritten on every save.

Saving runs on the background executor (`Saver.runAction`); no FX thread work, events, snapshots, undo frames,
`setUnsaved` or cache invalidation are involved: the open file's data and meta do not change.

## Goals / Non-Goals

**Goals:**
- Each saved channel header has `rhf_range` and `rhf_depth` scaled so that range per scan sample and depth per scan
  sample equal the source channel's.
- Saving leaves the open file's header buffers unchanged.

**Non-Goals:**
- `rhf_position`, `rhf_top` and the checksum (see proposal Non-goals).
- Moving header field offsets out of `DztWriter` into `DztHeader` or teaching `ObjectByteMapper` to write: the writer
  patches three fields, a general header writer is not needed for that.

## Decisions

### 1. Scale by the ratio of samples per scan

For a channel with source `rh_nsamp = N + 1` and `M` saved data samples:

```
saved rh_nsamp  = M + 1
saved rhf_range = rhf_range * (M + 1) / (N + 1)
saved rhf_depth = rhf_depth * (M + 1) / (N + 1)
```

This keeps `rhf_range / rh_nsamp` exactly (up to float rounding), which is how the sample time is defined. The values
are computed in `double` from the parsed source fields and stored as `float`.

Alternative: scale by the data sample ratio `M / N`. Rejected: it does not preserve `rhf_range / rh_nsamp`; the error
is small for long scans but grows for short ones.

When the source `rh_nsamp` is not positive, `rhf_range` and `rhf_depth` are written unchanged: there is no ratio to
apply, and the reopened file has a zero sample time just like the source.

### 2. Compute from parsed fields, write into a copy of the buffer

`writeHeaders` copies each header buffer into a new little-endian 1024-byte buffer, puts the three values at their
absolute offsets (`rh_nsamp` 4, `rhf_range` 26, `rhf_depth` 62, named as constants next to `RH_NSAMP_OFFSET`) and
writes the copy. The scaling reads only the parsed `DztHeader` fields, which always hold the source values.

The copy must set `ByteOrder.LITTLE_ENDIAN` explicitly: `ByteBuffer.duplicate()` and `allocate()` default to big
endian.

Alternatives:
- Keep patching `header.getBuffer()` in place. Rejected: it mutates the open file on every save, and if anything
  later read the scaled values back from the buffer, repeated saves would compound the scaling.
- Copy the `DztHeader` in `DztChannel.copy()`. Rejected: it only protects saves made through a copy and still leaves
  writing as a side effect on the passed channels.

## Risks / Trade-offs

- [Float rounding: the reopened sample time differs from the source in the last float digits] → Tests compare with a
  relative tolerance, not exact equality.
- [Other tools may interpret `rhf_position`/`rhf_top` against the new window after a top crop] → Unchanged from
  today; recorded as a non-goal for a follow-up.
