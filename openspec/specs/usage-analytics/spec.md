# usage-analytics Specification

## Purpose

Defines the anonymous usage events the application sends about user actions, so that product decisions can rely on
which features are used and on which file types.

## Requirements

### Requirement: Filter application event
The system SHALL send a `geohammer-filter-applied` analytics event when a filter is applied to a file. The event
SHALL carry exactly two properties: `filter`, the filter id, and `template`, the template name of the file, the same
value the `geohammer-file-opened` event carries for that file.

#### Scenario: Series filter applied to a data file
- **WHEN** the user applies a low-pass filter to a series of a CSV file opened with template `MagArrow`
- **THEN** the system sends `geohammer-filter-applied` with `filter` = `lowpass` and `template` = `MagArrow`

#### Scenario: No parameters or names in the event
- **WHEN** any filter application event is sent
- **THEN** it contains no filter parameter, series name or file name

### Requirement: Tracked filters
The system SHALL send the filter application event for these filters and their ids: low-pass `lowpass`, running
median `running_median`, time lag `timelag`, GPR background removal `background_removal`, GPR flatten surface
`flatten_surface`. Other operations SHALL NOT send it.

#### Scenario: Each tracked filter has its id
- **WHEN** the user applies running median, time lag, background removal or flatten surface to a file
- **THEN** the event `filter` is `running_median`, `timelag`, `background_removal` or `flatten_surface` respectively

#### Scenario: Grid render options are not tracked
- **WHEN** the user toggles hill shading, smoothing or analytic signal, or changes the range of a grid
- **THEN** no filter application event is sent

#### Scenario: Spread coordinates is not tracked
- **WHEN** the user runs spread coordinates on GPR files
- **THEN** no filter application event is sent

### Requirement: One event per processed file
The system SHALL send one filter application event for each file a filter is actually applied to.

#### Scenario: Apply to all
- **WHEN** the user applies a time lag filter with "Apply to all" and three open files share the selected template
- **THEN** the system sends three `geohammer-filter-applied` events, one per file

#### Scenario: Background removal over GPR files
- **WHEN** the user removes background and two of three open GPR files still have their background
- **THEN** the system sends two events with `filter` = `background_removal`, one per processed file

### Requirement: Event regardless of entry point
The system SHALL send the filter application event both when the filter is started from the tool panel and when it
is started through the MCP server, with the same properties.

#### Scenario: Filter applied through MCP
- **WHEN** an MCP client runs `apply_filter` with `running_median` on a data file
- **THEN** the system sends `geohammer-filter-applied` with `filter` = `running_median` and the file template

#### Scenario: Background removal through MCP
- **WHEN** an MCP client runs `remove_gpr_background` on a GPR file whose background is not removed
- **THEN** the system sends `geohammer-filter-applied` with `filter` = `background_removal`

### Requirement: No event without an application
The system SHALL NOT send the filter application event when no filter is applied by the user.

#### Scenario: Restored background removal on open
- **WHEN** a GPR file whose meta records removed background is opened
- **THEN** no filter application event is sent

#### Scenario: Filter not applied
- **WHEN** a series filter is requested for a series that has no chart and no new series is created
- **THEN** no filter application event is sent

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
