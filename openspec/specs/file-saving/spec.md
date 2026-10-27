# file-saving Specification

## Purpose

Defines how open files are written to disk with "Save" and "Save to..", and which target paths "Save to.." refuses
so that open files and source data are never overwritten by mistake.

## Requirements

### Requirement: Save to own path of a CSV-type file

When "Save to.." on a CSV-type file targets the path the file is currently open from, the system SHALL save the file
in place with the same result as "Save" and SHALL NOT report an error.

#### Scenario: Accepting the suggested path
- **WHEN** the user opens a CSV-type file, edits it, chooses "Save to.." and confirms the suggested name and folder
- **THEN** the file is written to its own path with the edits and is no longer marked unsaved
- **AND** the chart stays bound to the same file and no error is shown

#### Scenario: Per-file state is kept
- **WHEN** a CSV-type file with a computed grid is saved to its own path with "Save to.."
- **THEN** the grid of that file is still shown after the save

### Requirement: Save to an open file is refused

Apart from a CSV-type file saved to its own path, "Save to.." SHALL refuse a target path that belongs to a file open
in GeoHammer, leave the files unchanged, and show a message that names the target path, states that it is open in
GeoHammer and asks for another name or folder.

#### Scenario: Target is another open file
- **WHEN** files A and B are open and the user chooses "Save to.." on A with the path of B
- **THEN** nothing is written, both files keep their content and state
- **AND** the message says the target file is open in GeoHammer and does not suggest a lock by another program

#### Scenario: GPR export onto its own source
- **WHEN** a GPR file is open and the user chooses "Save to.." → "Single file" with the file's own path
- **THEN** the source file is not modified
- **AND** the message says the target file is open in GeoHammer and asks for another name or folder

### Requirement: Save to on a GPR file writes the kept traces

"Save to.." on a GPR file, to a single file or to separate lines, SHALL write exactly the traces shown in the chart,
in chart order and numbered from zero, with every channel limited to the file's sample range. Positions saved
alongside the traces SHALL belong to the same traces.

#### Scenario: First line removed, single file
- **WHEN** the user removes the first line of a DZT file and saves it with "Save to.." → "Single file"
- **THEN** the saved file contains only the remaining traces, in chart order, numbered from zero
- **AND** each saved trace has the GPS position of the same trace in the source file

#### Scenario: Separate lines after a crop
- **WHEN** the user removes the first line of a DZT file and saves it with "Save to.." → "Separate lines"
- **THEN** each line file contains only that line's traces, numbered from zero, with their own GPS positions

#### Scenario: Multi-channel sample crop
- **WHEN** the user crops the sample range of a multi-channel DZT file and saves it with "Save to.."
- **THEN** every channel in the saved file contains only the cropped sample range

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
