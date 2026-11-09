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

### Requirement: Data groups wrap instead of cutting text

The data groups SHALL be in one row when the chart area is wide enough. Otherwise whole groups SHALL wrap onto further
rows; the text of a group SHALL NOT be cut.

#### Scenario: Narrow chart area
- **WHEN** the chart area is too narrow for the three groups in one row
- **THEN** a group moves to the next row and every group line is shown in full

### Requirement: Demo opens a shipped demo file

The panel SHALL offer a GPR demo ("Gas Pipes GPR Demo") and a CSV demo ("Magnimbus Survey Demo") as links in the drop
zone under the drop hint. The GPR demo SHALL open the full Gas pipelines GPR survey used in the Quick Start; the CSV demo
SHALL open the full MagNIMBUS single-sensor magnetometer survey. The demo files SHALL be part of the installed app and
SHALL open without a network connection.

#### Scenario: GPR demo
- **WHEN** the user clicks the GPR demo link
- **THEN** the Gas pipelines GPR file is opened, its chart is shown and its track is shown on the map

#### Scenario: CSV demo offline
- **WHEN** the computer has no network connection and the user clicks the CSV demo link
- **THEN** the MagNIMBUS file is opened with the MagNIMBUS template

### Requirement: Demo opens a fresh copy of the demo file

A demo SHALL open a copy of the demo file in the user's GeoHammer folder, never the shipped file. Each click SHALL replace
the previous copy of that demo file and its edits (including saved marks and line changes), so every demo starts from the
original data. Saving a demo writes to the copy only.

#### Scenario: Demo edits are not kept
- **WHEN** the user opens the GPR demo, splits it into lines, saves, closes it and clicks the GPR demo link again
- **THEN** the opened file is the original continuous profile without the saved line changes

#### Scenario: Shipped demo file stays intact
- **WHEN** the user opens the CSV demo, applies a filter and saves
- **THEN** the demo file shipped with the app is unchanged

### Requirement: Demo links are disabled while a demo opens

While a demo file is being copied or opened, both demo links SHALL be disabled, so a second demo cannot be started
over the first one. The links SHALL be enabled again when opening finishes or fails.

#### Scenario: Double click on a demo
- **WHEN** the user clicks the GPR demo link twice in quick succession
- **THEN** the GPR demo file is opened once

### Requirement: Demo failure is reported

When a demo file is missing from the installation or its copy cannot be written, the app SHALL show an error naming
the demo and SHALL keep the welcome panel shown.

#### Scenario: Sample missing
- **WHEN** the GPR demo file has been deleted from the installation and the user clicks the GPR demo link
- **THEN** an error says the GPR demo could not be opened and the welcome panel stays shown

### Requirement: Panel links to documentation

The panel SHALL show links to the GeoHammer wiki: Quick Start, Template Format Specification and AI Integration. Clicking a link SHALL open it in the system browser.

#### Scenario: Quick Start link
- **WHEN** the user clicks Quick Start on the panel
- **THEN** the Quick Start wiki page opens in the system browser
