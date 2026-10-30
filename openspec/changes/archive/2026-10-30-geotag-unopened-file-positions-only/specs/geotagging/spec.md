# Spec Delta

## ADDED Requirements

### Requirement: Geotagging an unopened file changes only positions
When a GPR or sonar data file that is not open in GeoHammer is processed, the system SHALL write into its source file
only the new positions of the processed traces. Every trace (packet) of the source SHALL be kept in the same order
with the same samples, and the edits stored for the file in GeoHammer (removed lines, sample range, background
removal, marks) SHALL NOT be applied to the source and SHALL NOT be changed.

#### Scenario: Removed lines
- **WHEN** a SEG-Y GPR file whose first line was removed and saved in GeoHammer is closed and then geotagged
- **THEN** the source file has as many traces as before processing, each with its own samples
- **AND** opening the file in GeoHammer shows the same lines as before processing, with the first line removed

#### Scenario: Traces of removed lines keep their positions
- **WHEN** a SEG-Y GPR file whose first line was removed and saved in GeoHammer is closed and then geotagged with
  position files covering the whole file
- **THEN** the traces of the shown lines have the new positions
- **AND** the traces of the removed line are unchanged in the source file

#### Scenario: Sample range
- **WHEN** a SEG-Y GPR file whose sample range was cropped and saved in GeoHammer is closed and then geotagged
- **THEN** every trace of the source file has the same samples as before processing
- **AND** opening the file in GeoHammer shows the cropped sample range, cropped once

#### Scenario: Background removed
- **WHEN** a SEG-Y GPR file with background removed and saved in GeoHammer is closed and then geotagged
- **THEN** the samples of the source file are the same as before processing
- **AND** opening the file in GeoHammer shows the background removed once

#### Scenario: Marks kept
- **WHEN** a SEG-Y GPR file with marks saved in GeoHammer is closed and then geotagged
- **THEN** opening the file in GeoHammer shows the same marks on the same traces as before processing

#### Scenario: Sonar file with removed lines
- **WHEN** a sonar file whose first line was removed and saved in GeoHammer is closed and then geotagged
- **THEN** the source file contains every packet it contained before processing
- **AND** the packets of the shown lines have the new positions
- **AND** opening the file in GeoHammer shows the same lines as before processing, with the first line removed
