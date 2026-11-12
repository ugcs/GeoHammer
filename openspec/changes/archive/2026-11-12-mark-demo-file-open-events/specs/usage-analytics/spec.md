## REMOVED Requirements

### Requirement: Demo file opening event

**Reason**: A demo sent this event next to the regular file opening event, which could not be told apart from opening
a user file.

**Migration**: Filter `geohammer-file-opened` by `demo` = `true`.

## ADDED Requirements

### Requirement: Demo flag on file opening events

The `geohammer-file-opened` and `geohammer-file-open-error` analytics events SHALL carry a `demo` property: `true` when
the file is a demo file opened from the welcome panel, `false` otherwise. Opening a demo SHALL NOT send any other
analytics event.

#### Scenario: Demo opened
- **WHEN** the user clicks the GPR demo link and the demo file is opened
- **THEN** the system sends one `geohammer-file-opened` event with `template` = `sgy` and `demo` = `true`

#### Scenario: User file opened
- **WHEN** the user opens their own CSV file with template `MagNIMBUS`
- **THEN** the system sends `geohammer-file-opened` with `template` = `MagNIMBUS` and `demo` = `false`

#### Scenario: Demo failed to parse
- **WHEN** the user clicks a demo link and the copied demo file cannot be parsed
- **THEN** the system sends `geohammer-file-open-error` with `demo` = `true`

#### Scenario: Demo file missing
- **WHEN** the user clicks a demo link and the demo file is missing or cannot be copied
- **THEN** no analytics event is sent
