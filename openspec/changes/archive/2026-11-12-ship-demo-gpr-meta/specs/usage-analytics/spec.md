## ADDED Requirements

### Requirement: Demo file opening event

The system SHALL send a `geohammer-demo-file-opened` analytics event when a demo file is opened from the welcome
panel. The event SHALL carry exactly one property: `file_name`, the name of the shipped demo file — `demo-gpr.sgy`
for the GPR demo and `demo-csv.csv` for the CSV demo. The event is sent in addition to the `geohammer-file-opened`
event of the opened file.

#### Scenario: GPR demo opened
- **WHEN** the user clicks the GPR demo link and the demo file is opened
- **THEN** the system sends `geohammer-demo-file-opened` with `file_name` = `demo-gpr.sgy`

#### Scenario: CSV demo opened
- **WHEN** the user clicks the CSV demo link and the demo file is opened
- **THEN** the system sends `geohammer-demo-file-opened` with `file_name` = `demo-csv.csv`

#### Scenario: Demo failed to open
- **WHEN** the user clicks a demo link and the demo file is missing, cannot be copied or cannot be opened
- **THEN** no demo file opening event is sent
