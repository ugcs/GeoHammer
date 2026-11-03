## Purpose

Defines how NMEA log files are read into survey rows when opened, including sentences whose fields are empty or
malformed.

## ADDED Requirements

### Requirement: Values with missing data are skipped
When an NMEA file is opened, a value whose field in a sentence is empty or malformed SHALL be skipped, and the file
SHALL open. Skipping a value SHALL NOT drop other values of the same sentence or values of the other sentences of the
row. A value made of several fields (date and time, latitude and longitude, altitude and its unit) SHALL be skipped as
a whole when any of its fields is empty or malformed.

#### Scenario: Void RMC sentence
- **WHEN** a file contains an RMC sentence with status V, a time and a date, and empty position, speed and course fields
- **THEN** the file opens, and the row of that sentence has the date and time and no position, speed or course

#### Scenario: No-fix GGA sentence
- **WHEN** a file contains a GGA sentence with fix quality 0 and empty position and altitude fields
- **THEN** the file opens, the row of that sentence has GPS quality 0, and no position or altitude from that sentence

#### Scenario: Value from another sentence of the row
- **WHEN** a row has a void RMC sentence and a GGA sentence with a valid position
- **THEN** the row has the position from the GGA sentence and the time from the RMC sentence

#### Scenario: Malformed field
- **WHEN** a sentence has a field that holds text which cannot be read as its value
- **THEN** the file opens and only that value is missing from the row

### Requirement: Rows without position are kept
Each row of an NMEA file SHALL be kept when none of its sentences carries a position. Such a row SHALL have no position
and SHALL keep the other values read from its sentences.

#### Scenario: Rows recorded before the first fix
- **WHEN** a file starts with rows whose RMC and GGA sentences have empty position fields
- **THEN** the file opens with all rows, and the leading rows have no position

### Requirement: Values are used as written
The system SHALL use the values written in sentence fields regardless of the RMC status or the GGA fix quality of the
sentence.

#### Scenario: Position in a void RMC sentence
- **WHEN** an RMC sentence has status V and filled position fields
- **THEN** the row of that sentence has that position
