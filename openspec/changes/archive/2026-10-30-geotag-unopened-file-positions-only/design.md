# Design

## Context

See proposal.md - Why. Current state that shapes the approach:

- The geotagging window loads a file that is not open with the regular `GprFile.open` / `SonarFile.open`, which apply
  the sidecar: meta values cover the shown traces only, every trace gets the meta sample range, and `GprFile.open`
  runs background removal in place on the samples of the shown traces when the meta says so.
- `Geotagger.geotag` writes positions into `getGeoData()` (meta values), then `copyPositionsFromMeta()` copies them to
  the raw traces (`TraceFile.traces`) or replaces the NMEA sentences of the raw packets (`SonarFile.packets`) at each
  value's `getTraceIndex()`. For a file that is not open it then calls `save(getFile())`.
- `GprFile.save` writes `getTraces()` (the meta view: shown traces, meta sample range, samples after background
  removal). `SonarFile.save` writes only the packets referenced by meta values plus meta packets.
- In memory the raw data stays complete: `traces` holds every trace of the source and `packets` every packet. Only
  the GPR samples of shown traces are changed by background removal.
- `GprFile.open` builds `traces` one to one from `BinFile.load(...).getTraces()`, so raw trace `i` is the `i`-th
  trace `BinFile.load` reads from the source.

## Goals / Non-Goals

**Goals:**
- The geotagger writes into the source of an unopened file nothing but the positions it computed.
- No change to file loading or to the existing save paths.

**Non-Goals:**
- A general "save source" operation for meta files; the new methods serve the geotagger only.

## Decisions

### 1. Write positions into the source instead of saving the file
For an unopened `GprFile` or `SonarFile`, `Geotagger.save` calls a new `writePositionsToSource()` on the file instead
of `save(file)`. `CsvFile` keeps `save(file)`. Dispatch is a `switch` with type patterns in `Geotagger.save`.

*Alternative:* detach the meta in the geotagger, geotag and reapply it — rejected: there is no API to replace the
meta from outside the format, and background removal has already changed the samples in memory at load time and
cannot be undone.

*Alternative:* load files in the geotagging window without the sidecar (`openSource`) — rejected by the user: it
changes the loading path that many features depend on.

*Alternative:* open a copy of the source without its sidecar — rejected: copies large GPR files and swaps file paths.

### 2. GPR: patch trace headers of the source read from disk
`GprFile.writePositionsToSource()`:

```
source = getFile()
binFile = BinFile.load(source)                  raw headers and sample bytes, no meta, no background removal
check binFile trace count == traces.size()      else IOException: the source no longer matches the loaded file
for value in meta.getValues():
    i = value.getTraceIndex()
    write traces[i] latitude and longitude into binFile trace i header
binFile.save(source)
```

Sample bytes, the mark byte and all other header fields come from disk unchanged; headers of traces not in meta (removed
lines) are not touched. The latitude/longitude writing of `updateTraceBuffer` moves to a private `writePosition(Trace,
ByteBuffer)` used by both `save` and the new method, so the coordinate encoding stays in one place.

*Alternative:* write the in-memory raw traces with full samples — rejected: background removal has changed the
samples of shown traces.

*Alternative:* patch headers in place with `RandomAccessFile` — rejected: trace length is read per header, so the
file has to be walked anyway, and `BinFile` already does it; in-place writes add a second write path.

### 3. Sonar: write every raw packet
`SonarFile.writePositionsToSource()` writes every packet of `packets` to `getFile()` in order with `SvlogWriter`.
`copyPositionsFromMeta()` has already replaced the NMEA sentences of the geotagged packets; packets of removed lines are
unchanged.

### 4. Threads, undo, events
Unchanged: `geotag` runs on the shared executor; a file that is not open is not in the model, so there is no undo
frame, `setUnsaved`, `Model.reload` or event for it. Cancellation still stops between files, and a started write
completes.

## Risks / Trade-offs

- [`BinFile.load` holds a second copy of the GPR source in memory while writing] → one file at a time; the copy is
  the raw bytes, smaller than the decoded float samples already in memory.
- [The source changes on disk between loading in the window and processing] → the trace count check fails with an
  error for that file instead of writing positions to the wrong traces; changes that keep the count are not detected.
- [Positions filled for shown traces without GPS at load (`MissingValues.fillTracePositions`) are written to the
  source] → unchanged from today.
- [Sonar packets with an invalid checksum are skipped at load and are not written back] → unchanged from today's
  `save`.
