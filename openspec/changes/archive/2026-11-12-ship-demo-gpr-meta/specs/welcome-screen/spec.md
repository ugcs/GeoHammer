## MODIFIED Requirements

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

### Requirement: Demo opens a fresh copy of the demo file

A demo SHALL open a copy of the demo file in the user's GeoHammer folder, never the shipped file. When the demo file is
shipped with a sidecar, the copy SHALL get a copy of that sidecar, so the demo opens with the shipped line structure and
display settings. Each click SHALL replace the previous demo copies and their edits (including saved marks and line
changes), so every demo starts from the original data. Saving a demo writes to the copy only.

#### Scenario: Demo opens with shipped lines
- **WHEN** the user clicks the GPR demo link
- **THEN** the opened file shows the lines and display settings of the shipped sidecar

#### Scenario: Demo edits are not kept
- **WHEN** the user opens the GPR demo, splits a line, saves, closes it and clicks the GPR demo link again
- **THEN** the opened file has the shipped lines without the saved line changes

#### Scenario: Shipped demo file stays intact
- **WHEN** the user opens the CSV demo, applies a filter and saves
- **THEN** the demo file shipped with the app is unchanged
