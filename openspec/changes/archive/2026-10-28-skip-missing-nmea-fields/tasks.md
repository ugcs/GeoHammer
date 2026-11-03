## 1. Guarded reads in NmeaParser

- [x] 1.1 Add static `NmeaParser.parseOrNull` (returns `null` on `RuntimeException`); verify by compiling
- [x] 1.2 Read fields through `parseOrNull` in `parseLocation(Sentence)`, `parseAltitude`, `parseHeading`,
  `parseTime(Sentence)`, `parseTimeOfDay` and `parseFixQuality`; verify by compiling
- [x] 1.3 Add `NmeaParserTest` (void RMC: no position, time kept; no-fix GGA: no position or altitude, fix quality 0;
  empty time in `parseTimeOfDay`) and verify it passes

## 2. NMEA files

- [x] 2.1 In `NmeaFile.parseValues`, use the guarded `Sentence`-based methods for RMC/GGA position, RMC/ZDA time and
  GGA fix quality, and wrap speed, course, altitude with unit, depth, offset, temperature and the XDR measurement list
  in `parseOrNull`; verify by compiling
- [x] 2.2 Add `NmeaFileTest` opening a temp file with a valid group, a void RMC group, a no-fix GGA group and a void RMC
  plus valid GGA group; assert row count, positions, time and GPS quality per the `nmea-loading` scenarios, and verify
  it passes

## 3. Sonar files

- [x] 3.1 Add `SvlogParserTest` with NMEA wrapper packets holding a void RMC and a no-fix GGA; assert no position, time
  kept and fix quality 0, and verify it passes

## 4. Verify

- [x] 4.1 Run `mvn clean package`; fix new compilation errors and Error Prone/NullAway warnings, and verify all tests
  pass
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference; fix critical and major issues, and report the scores and any issues left open
