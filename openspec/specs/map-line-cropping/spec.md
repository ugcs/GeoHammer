# map-line-cropping Specification

## Purpose

Lets users draw a crop area on the map with the Select Area tool and crop survey lines of the open files to it, and
defines how the tool and its crop area behave as files are opened and closed.

## Requirements

### Requirement: Select Area turns off when the last file is closed

When the last open file is closed while the Select Area tool is on, the tool SHALL turn off: its button SHALL be shown
released and disabled, and the crop area SHALL no longer be drawn on the map. This SHALL hold for closing a single
file and for Close All.

#### Scenario: Closing the only open file
- **WHEN** one file is open, the Select Area tool is on, and the user closes that file
- **THEN** the Select Area button is released and disabled, and the crop area is no longer drawn on the map

#### Scenario: Close All
- **WHEN** several files are open, the Select Area tool is on, and the user chooses Close All
- **THEN** after all files are closed the Select Area button is released and disabled, and no crop area is drawn

#### Scenario: Opening a file afterwards
- **WHEN** the tool was turned off by closing the last file and the user opens a file
- **THEN** the Select Area button is released and enabled, and turning it on draws a new crop area fitted to the
  open files

### Requirement: Select Area stays on while files remain open

Closing a file while other files remain open SHALL NOT turn the Select Area tool off or change its crop area.

#### Scenario: Closing one of several files
- **WHEN** two files are open, the Select Area tool is on, and the user closes one of them
- **THEN** the Select Area button stays pressed and enabled, and the crop area stays where it was

### Requirement: Crop keeps only points inside the crop area

Cropping SHALL keep the points of each file whose coordinates lie inside the crop area and remove all other points.
Points without coordinates SHALL be treated as outside the area and removed. A file with no points with coordinates
inside the area SHALL stay unchanged.

#### Scenario: Points inside and outside the area
- **WHEN** a file has points both inside and outside the crop area and the user crops
- **THEN** only the points inside the area remain in the file

#### Scenario: File geotagged only partly
- **WHEN** a file has points with coordinates inside the crop area followed by points without coordinates, and the
  user crops
- **THEN** the crop completes, the points inside the area remain and the points without coordinates are removed

#### Scenario: File without coordinates next to a georeferenced file
- **WHEN** a GPR file without coordinates is open next to a CSV file with points inside the crop area, and the user
  crops
- **THEN** the CSV file keeps only its points inside the area and the GPR file stays unchanged
