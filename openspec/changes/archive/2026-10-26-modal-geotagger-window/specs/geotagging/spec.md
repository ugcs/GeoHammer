# Spec Delta

## Purpose

Defines the geotagging window, which assigns positions from position files to data files by time, and how it
interacts with files open in GeoHammer so that processing never writes data the user did not intend to keep.

## ADDED Requirements

### Requirement: Geotagging window blocks other windows
While the geotagging window is open, the main window and every other GeoHammer window SHALL NOT accept user input,
so files cannot be opened, closed, edited or saved there until the geotagging window is closed.

#### Scenario: Closing a file while geotagging is open
- **WHEN** a CSV file is open and edited without saving and the user opens the geotagging window
- **THEN** the user cannot close the file in the main window until the geotagging window is closed
- **AND** pressing "Process" updates the file in the main window and does not write it to disk

#### Scenario: Window stays above the main window
- **WHEN** the geotagging window is open and the user clicks the main window
- **THEN** the geotagging window stays in front of the main window

### Requirement: Closing the window cancels processing
When the user closes the geotagging window with Close or the window close button while processing runs, the
system SHALL cancel processing, keep the window open and blocking with an indication that it is cancelling, and close
it only after processing has stopped.

#### Scenario: Close during processing
- **WHEN** processing runs and the user presses Close
- **THEN** the window shows that processing is being cancelled and Close cannot be pressed again
- **AND** the window closes once processing has stopped
- **AND** the status bar reports that processing was cancelled

#### Scenario: Close when idle
- **WHEN** no processing runs and the user presses Close
- **THEN** the window closes immediately

### Requirement: Cancellation stops between files
When processing is cancelled, the file being processed SHALL be processed completely, including writing it to disk or
updating it in GeoHammer, and files not yet reached SHALL NOT be changed. Files already processed SHALL keep their new
positions.

#### Scenario: Cancel in the middle of a file
- **WHEN** data files A, B and C are processed, A is finished and processing is cancelled while B is processed
- **THEN** A and B have the new positions
- **AND** C has the same positions as before processing, in memory and on disk

#### Scenario: Cancel while a file is being saved
- **WHEN** processing is cancelled while a data file that is not open in GeoHammer is being written to disk
- **THEN** the file on disk is complete and contains the new positions

#### Scenario: Cancel after the last file
- **WHEN** processing is cancelled after the last data file has been processed
- **THEN** all files have the new positions and the status bar reports that processing finished successfully

### Requirement: Processing uses the files listed at start
Processing SHALL use the position and data files listed in the window when "Process" is pressed; changes to the
lists during processing SHALL NOT affect the run.

#### Scenario: Lists cleared during processing
- **WHEN** processing runs and the window lists are cleared or changed
- **THEN** processing continues or stops on the files it started with and does not skip or repeat files
