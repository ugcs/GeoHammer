## Context

marineapi parses sentence fields lazily: a getter reads its field when called. An empty field throws
`DataNotAvailableException`; a malformed one throws `ParseException` (a subclass of it) or a plain
`NumberFormatException` / `IllegalArgumentException` from its value types (`Time(String)`, `Date(String)`,
`PositionParser.parseDegrees`, `GpsFixQuality.valueOf`). Our own conversion can throw too (`LocalDate.of` in
`NmeaParser.parseTime`).

Getters are called in two places:

```
NmeaFile.syncMeta                       SonarFile.syncMeta
  per row (sentence group)                first position/time search
    parseValues(row, sentence)            per packet
      rmc/gga/zda/dbt/dpt/mtw/xdr           SvlogParser.parseValues
      getters called directly                 NmeaParser.parseLocation(Sentence), parseAltitude,
      + NmeaParser.parseLocation(Position)    parseHeading, parseTime(Sentence), parseTimeOfDay,
      + NmeaParser.parseFixQuality            parseFixQuality
```

Only `NmeaParser.parseValue(Measurement)` (XDR) guards its read today. `syncMeta` runs on load (`loadMeta`, executor
thread), after line transforms (`TraceTransform`), on undo restore (`Snapshot.restoreFile`), on `loadFrom` and from
`HorizontalProfile`. It only derives rows from the source sentences; it does not change file data.

## Goals / Non-Goals

**Goals:**
- One guarded read per value, shared by the NMEA and sonar paths.

**Non-Goals:**
- Guarding sentence creation in `SvlogParser.parseNmeaSentence` (unsupported wrapped sentences).
- Any change to grouping (`readSentences`), saving or meta.
- Counting or logging skipped values: the guard returns `null` silently, keeping the change small.

## Decisions

### Guard each value, not each sentence
A value is read through a guard that returns `null` when the read throws. A try/catch around a whole sentence would
leave the row with whatever setters ran before the failing getter, so the result would depend on statement order, and a
void RMC would lose its time.

A value made of several fields is read in one guarded call: date + time (`parseTime(date, time)`), latitude + longitude
(+ hemispheres), altitude + unit. Reading altitude and unit separately would allow an altitude column with a missing
unit.

### Catch `RuntimeException`, not only `DataNotAvailableException`
Malformed fields surface as several unrelated runtime exceptions (see Context), and the spec treats empty and malformed
alike. The guarded lambdas contain only marineapi getters and value conversion, so a broad catch cannot hide a bug in
row building. This matches `NmeaReader.readSentence`, which already skips sentences on `RuntimeException`.

### Guard lives in `NmeaParser`
`NmeaParser` gets a static `parseOrNull(Supplier)` that runs a read and returns `null` when it throws. It is stateless,
so parsers stay shareable and no synchronization is needed. The `Sentence`-based methods (`parseLocation(Sentence)`,
`parseAltitude`, `parseHeading`, `parseTime(Sentence)`, `parseTimeOfDay`, `parseFixQuality`) read their fields through
it, which fixes the sonar path without touching `SvlogParser` or `SonarFile`. `parseValue(Measurement)` replaces its own
catch with it.

`NmeaFile.parseValues` uses the guarded `Sentence`-based methods where they fit (RMC and GGA position, RMC and ZDA time,
GGA fix quality) and wraps the remaining direct getter reads (speed, course, altitude with unit, depth, offset,
temperature, XDR measurement list) in `parseOrNull`.

### Data, threads and events
No file data changes: no snapshot, `setUnsaved` or cache invalidation is involved, and no events are added. Threads are
unchanged: `syncMeta` runs where it runs today (executor thread on load, the transform or undo caller otherwise).

## Risks / Trade-offs

- [A file with no position at all now opens instead of failing] → Requested behavior; rows without coordinates are
  already handled by map and crop paths (#1053).
- [Skipped values leave no trace in the log, and a broad catch could hide a marineapi defect] → Accepted: the guarded
  reads are single getters, and the missing values are visible in the chart and on the map.
