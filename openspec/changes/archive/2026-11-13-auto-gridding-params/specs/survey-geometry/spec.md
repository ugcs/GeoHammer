## Purpose

Estimates the geometry of a survey from the track of a data file, such as the spacing between survey lines, so that
processing parameters can be derived from how the data was collected.

## ADDED Requirements

### Requirement: Line spacing from track geometry

The line spacing of a data file SHALL be estimated from the positions of its rows in row order. Survey lines SHALL be
detected from the track geometry; the Line column and the line structure of the file SHALL NOT be used. Rows without a
position SHALL be skipped. The estimate SHALL be the typical distance between adjacent parallel survey lines in meters;
where the lines are shifted in pairs, so that narrow and wide gaps alternate, it SHALL be the average of both gaps.

#### Scenario: Single track without a Line column
- **WHEN** a file has no Line column and its track is a single serpentine of parallel lines 5 m apart joined by turns
- **THEN** the estimated line spacing is within 10% of 5 m

#### Scenario: Line structure does not affect the estimate
- **WHEN** the same track is estimated once as a single line and once split into lines
- **THEN** both estimates are equal

#### Scenario: Rotated survey
- **WHEN** the survey lines of a track run at any heading
- **THEN** the estimated line spacing is within 10% of the planned spacing

#### Scenario: Rows without positions
- **WHEN** some rows of a file have no latitude or longitude
- **THEN** those rows are ignored and the estimate is made from the remaining rows

#### Scenario: Lines in pairs
- **WHEN** the gaps between the survey lines alternate between 0.6 m and 1.4 m
- **THEN** the estimated line spacing is within 10% of 1 m

#### Scenario: Two lines
- **WHEN** the track of a file is two parallel straight lines 5 m apart joined by a turn
- **THEN** the estimated line spacing is within 10% of 5 m

### Requirement: Robust line spacing

The line spacing estimate SHALL stay within 10% of the planned spacing for surveys of straight parallel lines that
also contain tie lines across the survey lines taking up to 20% of the track length, turns removed from the data, survey
lines interrupted by gaps, lines flown out of order, a re-flown line, every line present twice, two survey blocks side
by side along the line direction, or position noise of up to a tenth of the line spacing.

#### Scenario: Tie lines
- **WHEN** a survey of lines 4 m apart has tie lines across them taking 15% of the track length
- **THEN** the estimated line spacing is within 10% of 4 m

#### Scenario: Turns removed
- **WHEN** the turns between the survey lines were removed and consecutive rows jump from the end of one line to the
  start of the next
- **THEN** the estimated line spacing is within 10% of the planned spacing

#### Scenario: Lines flown out of order
- **WHEN** lines 1, 3, 5 and then 2, 4, 6 of a survey are flown
- **THEN** the estimated line spacing is within 10% of the distance between lines 1 and 2

#### Scenario: Every line present twice
- **WHEN** the rows of a survey are present twice in a file
- **THEN** the estimated line spacing is within 10% of the planned spacing

#### Scenario: Survey lines with gaps
- **WHEN** each survey line has gaps without data along it
- **THEN** the estimated line spacing is within 10% of the planned spacing

#### Scenario: Two blocks side by side along the line direction
- **WHEN** a file contains two survey blocks with the same line spacing placed one after the other along the line
  direction with their lines laterally offset by half of the spacing
- **THEN** the estimated line spacing is within 10% of the planned spacing

### Requirement: No estimate without parallel lines

The line spacing SHALL NOT be estimated when the track has fewer than two parallel straight lines, including a
single straight line, a line flown back and forth over the same path, a spiral, and a file without positions. The
absence of an estimate SHALL be reported to the caller instead of a value or an error.

#### Scenario: Single straight line
- **WHEN** the track of a file is one straight line
- **THEN** no line spacing is estimated

#### Scenario: Line flown back and forth
- **WHEN** the track of a file is one line flown there and back over the same path, with position noise
- **THEN** no line spacing is estimated

#### Scenario: No positions
- **WHEN** a file has no rows with a position
- **THEN** no line spacing is estimated

### Requirement: Estimate time

Estimating the line spacing of a file with 1,000,000 positions SHALL take less than one second on a typical
workstation.

#### Scenario: Large file
- **WHEN** the line spacing of a file with 1,000,000 positions is estimated
- **THEN** the estimate completes in less than one second
