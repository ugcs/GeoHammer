# welcome-screen Specification

## Purpose

Defines the welcome panel shown in the chart area while no file is open: how it tells the user what can be opened and
how, the demo files it opens and the documentation it links to.

## Requirements

### Requirement: Welcome panel is shown while no file is open

The chart area SHALL show the welcome panel when no file is open, including at startup. The panel SHALL be hidden as
soon as any file is open, whether its chart is expanded or collapsed. The map, the toolbar and the right tool panel
SHALL stay as they are without files.

#### Scenario: Startup without files
- **WHEN** the app starts with no files passed to it
- **THEN** the chart area shows the welcome panel

#### Scenario: First file opened
- **WHEN** the welcome panel is shown and the user opens a file
- **THEN** the panel is hidden and the chart of the file is shown

#### Scenario: Last file closed
- **WHEN** two files are open and the user closes both
- **THEN** the welcome panel is shown again after the second file is closed

#### Scenario: Only collapsed charts
- **WHEN** one file is open and its chart is collapsed
- **THEN** the welcome panel is not shown

### Requirement: Panel explains how to open files

The panel SHALL tell the user that files and whole folders, one or several at a time, can be dropped onto the chart
area. The panel SHALL NOT duplicate the Open files toolbar button. The drop hint, the demo links and the format groups
SHALL be framed together as the drop zone; the documentation links SHALL be outside the frame.

#### Scenario: Hint shown
- **WHEN** the welcome panel is shown
- **THEN** it says that files or folders can be dropped here and that several files and whole folders can be opened at
  once

#### Scenario: Folder dropped on the panel
- **WHEN** the user drops a folder with GPR files onto the panel
- **THEN** the supported files from the folder are opened, as when the folder is dropped elsewhere on the window

### Requirement: Drop zone is highlighted while dragging files

While files are dragged over the chart area, the drop zone frame on the panel SHALL be highlighted. The highlight SHALL be
removed when the drag leaves the chart area, is dropped or is cancelled.

#### Scenario: Drag over and away
- **WHEN** the user drags a file over the chart area and then moves it back out without dropping
- **THEN** the drop zone is highlighted while the file is over the chart area and not highlighted after it leaves

### Requirement: Panel describes supported data

The panel SHALL show three groups of supported data, each with an icon of the data it holds and two lines
naming the data with its format in parentheses: GPR (a hyperbola icon; "GPR radargrams (SEG-Y)", "GSSI radargrams
(DZT)"), sensor tables (an anomaly peak icon; "Sensor tables (CSV, TXT, XYZ)", "GNSS positions (POS)") and navigation
and sonar (a sonar ping icon; "GPS tracks (NMEA)", "Sonar logs (SVLOG)").

#### Scenario: Data groups shown
- **WHEN** the welcome panel is shown
- **THEN** the drop zone shows the GPR, sensor tables and navigation and sonar groups, each with a distinct icon and
  its two lines

### Requirement: Data icons match the group text

Each group icon SHALL have the color of its group text in every theme and SHALL be vertically centered on the group's
two lines.

#### Scenario: Icon color follows the theme
- **WHEN** the light or the dark theme is selected and the welcome panel is shown
- **THEN** each group icon is drawn in the same color as its group text

#### Scenario: Icon centered on the lines
- **WHEN** the welcome panel is shown
- **THEN** the middle of each group icon is level with the middle of its two lines

### Requirement: Drop hint leads the panel

The upload icon above the drop hint SHALL be drawn in the color of the hint text in every theme, and the data groups
SHALL be fainter than the hint and its icon.

#### Scenario: Upload icon follows the theme
- **WHEN** the light or the dark theme is selected and the welcome panel is shown
- **THEN** the upload icon is drawn in the same color as the drop hint, and the data groups and their icons are fainter

### Requirement: Data groups wrap instead of cutting text

The data groups SHALL be in one row when the chart area is wide enough. Otherwise whole groups SHALL wrap onto further
rows; the text of a group SHALL NOT be cut.

#### Scenario: Narrow chart area
- **WHEN** the chart area is too narrow for the three groups in one row
- **THEN** a group moves to the next row and every group line is shown in full

### Requirement: Demo opens a shipped demo file

The panel SHALL offer a GPR demo ("GPR Grid Survey Demo") and a CSV demo ("MagNIMBUS Survey Demo") as links in the drop
zone under the drop hint. The GPR demo SHALL open a GPR grid survey of parallel lines over one area; the CSV demo
SHALL open the full MagNIMBUS single-sensor magnetometer survey. The demo files SHALL be part of the installed app and
SHALL open without a network connection.

#### Scenario: GPR demo
- **WHEN** the user clicks the GPR demo link
- **THEN** the GPR grid survey file is opened, its chart is shown and its track is shown on the map

#### Scenario: CSV demo offline
- **WHEN** the computer has no network connection and the user clicks the CSV demo link
- **THEN** the MagNIMBUS file is opened with the MagNIMBUS template

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

### Requirement: Demo failure is reported

When a demo file is missing from the installation, the app SHALL show an error naming the demo and SHALL keep the
welcome panel shown.

#### Scenario: Sample missing
- **WHEN** the GPR demo file has been deleted from the installation and the user clicks the GPR demo link
- **THEN** an error says the GPR demo could not be opened and the welcome panel stays shown

### Requirement: Panel links to documentation

The panel SHALL show links to the GeoHammer wiki: Quick Start, Template Format Specification and AI Integration. Clicking a link SHALL open it in the system browser.

#### Scenario: Quick Start link
- **WHEN** the user clicks Quick Start on the panel
- **THEN** the Quick Start wiki page opens in the system browser
