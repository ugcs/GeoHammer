# Spec Delta

## Purpose

Defines what happens when a file that is already open in GeoHammer is opened again, so that reopening never loses
unsaved edits without consent and never leaves a second, detached copy of the file.

## ADDED Requirements

### Requirement: Reopening a file never opens a second copy

When a file that is already open is opened again by any means, the system SHALL keep a single chart for it. When the
file is reloaded or the user chooses to keep it, the system SHALL select and scroll to its chart.

#### Scenario: Opening an open file from the Open dialog
- **WHEN** A.csv is open and the user opens A.csv again with the Open dialog
- **THEN** there is one chart of A.csv, it is selected and scrolled into view

### Requirement: An unmodified file is reloaded silently

When a file without unsaved changes is opened again, the system SHALL reload it from disk without asking.

#### Scenario: File changed on disk by another program
- **WHEN** A.csv is open without unsaved changes, another program changes A.csv on disk and the user opens A.csv again
- **THEN** the chart shows the new content of A.csv and no dialog is shown

### Requirement: Unsaved changes are confirmed before reload

When a file with unsaved changes is opened again from the Open dialog, by dropping the file itself, from the command
line or from the operating system, the system SHALL ask whether to keep the open file or reload it from disk before
reading it. "Keep" SHALL leave the file and its changes unchanged and select its chart. "Reload" SHALL reload the file.

#### Scenario: Keep the edits
- **WHEN** the user crops lines of A.csv, opens A.csv again and chooses "Keep"
- **THEN** A.csv keeps the crop and is still marked unsaved

#### Scenario: Reload from disk
- **WHEN** the user crops lines of A.csv, opens A.csv again and chooses "Reload"
- **THEN** A.csv shows all its lines as stored on disk and is not marked unsaved

#### Scenario: Several modified files
- **WHEN** A.csv and B.csv both have unsaved changes and the user opens both of them again in one Open dialog
- **THEN** the system asks for each of the two files separately

### Requirement: A reload replaces the file as if it was closed and opened

A reloaded file SHALL have the content of the file opened in a new session. Per-file state of the replaced file SHALL be
dropped as when the file is closed: its undo steps, grid, selected series and collapsed state. The chart of the reloaded
file SHALL take the position of the replaced chart in the chart list.

#### Scenario: Undo after reload
- **WHEN** the user crops lines of A.csv, opens A.csv again, chooses "Reload" and then chooses Undo
- **THEN** Undo does not change A.csv

#### Scenario: Chart position
- **WHEN** A.csv, B.csv and C.csv are open in this order and the user reopens B.csv
- **THEN** the charts are still shown in the order A.csv, B.csv, C.csv

#### Scenario: GPR position file
- **WHEN** a GPR file with a position file is reloaded
- **THEN** its ground profile is read from the position file as on open

### Requirement: Modified files opened through a folder are kept

When an opened folder contains a file that is open with unsaved changes, the system SHALL NOT ask about it, SHALL keep
the file and its changes unchanged, and SHALL report in the status bar that the file was kept. Files of the folder
without unsaved changes SHALL be reloaded as when opened individually.

#### Scenario: Folder with a modified file
- **WHEN** A.csv has unsaved changes, B.csv is open without changes, and the user opens their folder
- **THEN** no dialog is shown, A.csv keeps its changes, B.csv is reloaded from disk
- **AND** the status bar reports that A.csv was kept

### Requirement: A reload does not overwrite concurrent edits

When the open file is changed while its reload is being read, the system SHALL NOT apply the reload, SHALL keep the
file as changed, and SHALL report in the status bar that the reload was skipped.

#### Scenario: Edit during a long reload
- **WHEN** a large GPR file without unsaved changes is being reloaded and the user removes a line of it before the
  reload completes
- **THEN** the line stays removed, the file is not replaced, and the status bar reports the skipped reload
