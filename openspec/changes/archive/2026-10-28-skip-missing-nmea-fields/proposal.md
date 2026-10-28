## Why

Opening an NMEA log fails when any sentence has an empty field (#1021). GPS receivers write void RMC sentences (status
V) and no-fix GGA sentences (quality 0) with empty position fields at startup and during signal loss, so real survey
logs routinely contain them, and one such sentence rejects the whole file. Sonar (svlog) files that wrap NMEA sentences
read them with the same parsing and are exposed to the same failure.

## What Changes

- A value whose field is empty or malformed in an NMEA sentence is skipped instead of failing the file load. Other
  values of the same sentence and of the other sentences of the row are kept (e.g. a void RMC still gives the row its
  date and time).
- Rows whose sentences carry no position are kept, without a position.
- Values are taken as written: positions are not filtered by RMC status or GGA fix quality.
- The same rules apply to NMEA sentences wrapped in sonar files: a skipped value leaves the last known value in place.

## Non-goals

- Filtering stale positions of void RMC or no-fix GGA sentences.
- Notifying the user or logging a warning about skipped values.
- Handling unsupported or unparsable sentences inside sonar files (only fields of parsed sentences are covered).
- Changing the sonar file failure when no position and no time can be read from the whole file.

## Capabilities

### New Capabilities

- `nmea-loading`: how NMEA log files are read into rows when opened, including sentences with missing or malformed
  fields.
- `sonar-loading`: how sonar (svlog) files derive positions, time and NMEA-based values from wrapped NMEA sentences,
  including sentences with missing or malformed fields.

### Modified Capabilities

None.

## Impact

- `format/nmea`: `NmeaParser` (field reads that may be missing), `NmeaFile` (row values from sentences).
- `format/svlog`: sonar files are fixed through `NmeaParser`; no code changes there.
- No changes to saving, meta format, events or dependencies.
