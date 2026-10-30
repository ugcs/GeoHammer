# sonar-loading Specification

## Purpose

Defines how sonar (svlog) files derive position, time and other navigation values from the NMEA sentences recorded
in them, including sentences whose fields are empty or malformed.

## Requirements

### Requirement: Wrapped NMEA values with missing data are skipped

When a sonar file is opened, a value whose field in a recorded NMEA sentence is empty or malformed SHALL be skipped, and
the file SHALL open. Skipping a value SHALL NOT drop other values of the same sentence. Rows after a skipped value SHALL
keep the last value read before it.

#### Scenario: Void RMC sentence in a sonar file
- **WHEN** a sonar file records an RMC sentence with status V, a time and a date, and empty position fields
- **THEN** the file opens, the time is taken from that sentence, and rows keep the last known position

#### Scenario: No-fix GGA sentence in a sonar file
- **WHEN** a sonar file records a GGA sentence with fix quality 0 and empty position and altitude fields
- **THEN** the file opens, GPS quality 0 is taken from that sentence, and rows keep the last known position and
  altitude

### Requirement: Sonar file needs a position or a time

A sonar file SHALL fail to open when no position and no time can be read from any of its recorded sentences.

#### Scenario: Only void sentences
- **WHEN** every recorded NMEA sentence of a sonar file has empty position and time fields
- **THEN** the file fails to open

### Requirement: Wrapped values are used as written

The system SHALL use the values written in recorded NMEA sentence fields regardless of the RMC status or the GGA fix
quality of the sentence.

#### Scenario: Position in a no-fix GGA sentence
- **WHEN** a recorded GGA sentence has fix quality 0 and filled position fields
- **THEN** rows from that sentence on have that position
