# Spec Delta

## ADDED Requirements

### Requirement: Save to on a GPR file keeps every mark

"Save to.." on a SEG-Y GPR file, to a single file or to separate lines, SHALL write every marked trace as marked and
every other trace as unmarked, regardless of how many marks the saved traces have. Each marked trace SHALL be stored
with the mark value 1.

#### Scenario: More than 255 marks, single file
- **WHEN** a SEG-Y GPR file has 300 marked traces and the user saves it with "Save to.." → "Single file"
- **THEN** the saved file, opened in GeoHammer, has exactly the same 300 traces marked

#### Scenario: More than 255 marks in one line, separate lines
- **WHEN** one line of a SEG-Y GPR file has 300 marked traces and the user saves it with "Save to.." → "Separate
  lines"
- **THEN** that line's file, opened in GeoHammer, has exactly the same 300 traces marked

#### Scenario: Mark value in the trace header
- **WHEN** a SEG-Y GPR file with marks is saved with "Save to.."
- **THEN** the mark byte of every marked trace is 1 and of every other trace is 0
