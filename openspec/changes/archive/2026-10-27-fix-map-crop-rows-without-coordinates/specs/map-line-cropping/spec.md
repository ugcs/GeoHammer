# Spec Delta

## ADDED Requirements

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
