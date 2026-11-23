## REMOVED Requirements

### Requirement: Demo opens a fresh copy of the demo file

**Reason**: Demo files are opened in place and cannot be saved in place, so a copy is no longer needed to keep the
installation intact and every demo fresh.

**Migration**: See "Demo opens the shipped file in place" and "Demo files are read-only".

### Requirement: Demo links are disabled while a demo opens

**Reason**: A demo opens like a file opened with Open or by drag and drop, which do not guard against a repeated
start either. A second click while a demo opens loads the demo again, and the second load replaces the first.

**Migration**: None.

## ADDED Requirements

### Requirement: Demo opens the shipped file in place

A demo SHALL open the demo file shipped with the app in place, together with its shipped sidecar, so the demo opens
with the shipped line structure and display settings. Nothing SHALL be copied into the user folder.

#### Scenario: Demo opens with shipped lines
- **WHEN** the user clicks the GPR demo link
- **THEN** the opened file shows the lines and display settings of the shipped sidecar

### Requirement: Demo files are read-only

A demo file SHALL NOT be saved in place: Save SHALL be disabled while a demo file is selected, and Save all SHALL save
the other open files only. Save to SHALL stay available. A demo file is one of the demo files shipped with the app,
however it was opened, so every demo starts from the original data; any other file stays writable wherever it is.

#### Scenario: Save disabled for a demo
- **WHEN** the user opens the GPR demo and splits a line
- **THEN** the Save button is disabled and Save to is enabled

#### Scenario: Save all skips demo files
- **WHEN** a demo file and a user file are open with unsaved changes and the user clicks Save all
- **THEN** the user file is saved and the demo file shipped with the app is unchanged

#### Scenario: User file next to a folder named like the demo folder
- **WHEN** the user opens their own file from a folder named `data`
- **THEN** the Save button is enabled and Save all saves the file

#### Scenario: CSV demo saved to a new file
- **WHEN** the user opens the CSV demo, applies a filter and saves it with Save to under a new name
- **THEN** the chart shows the new file, Save is enabled for it and the demo file shipped with the app is unchanged

#### Scenario: Demo edits are not kept
- **WHEN** the user opens the GPR demo, splits a line, closes it without saving and clicks the GPR demo link again
- **THEN** the opened file has the shipped lines

### Requirement: Demo outputs have no default folder

Every place that by default writes output next to the selected file SHALL leave the output location to the user when
the selected file is a demo file, so the user chooses where the results go: the Save to dialogs start without a
folder, the output folder of a processing script is empty and the projection export path is empty. For other files
these defaults stay the folder of the file.

#### Scenario: Script output for a demo
- **WHEN** the user opens the CSV demo and selects a script with an output folder parameter that has no value
- **THEN** the output folder is empty, and running the script without choosing one reports the missing output folder

#### Scenario: Projection export of a demo
- **WHEN** the user opens the GPR demo and opens the projection export
- **THEN** the export path is empty and Export is disabled until the user selects a path

#### Scenario: User file outputs
- **WHEN** the user opens their own file and selects a script with an output folder parameter that has no value
- **THEN** the output folder is the folder of the file

## MODIFIED Requirements

### Requirement: Demo failure is reported

When a demo file is missing from the installation, the app SHALL show an error naming the demo and SHALL keep the
welcome panel shown.

#### Scenario: Sample missing
- **WHEN** the GPR demo file has been deleted from the installation and the user clicks the GPR demo link
- **THEN** an error says the GPR demo could not be opened and the welcome panel stays shown
