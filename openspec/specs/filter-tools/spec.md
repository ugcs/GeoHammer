# filter-tools Specification

## Purpose

Defines how processing tools in the right panel (filters, gridding, quality control, GPR background and elevation)
behave while one of their tasks runs, so that a tool never runs two tasks at once and never applies a stale result.

## Requirements

### Requirement: Tool actions stay disabled while a task runs

While a tool's task runs, the tool's Apply and Apply to all actions SHALL be disabled. Selecting another file, another
series or a file of another template, or a change of the tool's input, SHALL NOT enable them before the task completes.

#### Scenario: Series changed during gridding
- **WHEN** gridding is running and the user selects another series of the same file
- **THEN** Apply and Apply to all of the gridding tool stay disabled until the gridding completes

#### Scenario: File of another template selected during a filter
- **WHEN** a low-pass filter is running and the user selects a file of another template whose saved filter length is
  valid
- **THEN** Apply and Apply to all of the low-pass tool stay disabled until the filter completes

### Requirement: Tool actions follow input validity when a task completes

When a tool's task completes, the tool's Apply and Apply to all actions SHALL be enabled only if the tool's current
input is valid.

#### Scenario: Input became invalid during the task
- **WHEN** a running median is running, the user selects a file of another template that has no saved window, and the
  running median completes
- **THEN** Apply and Apply to all of the running median tool stay disabled

#### Scenario: Input valid at completion
- **WHEN** a time lag completes and the tool's shift is valid
- **THEN** Apply and Apply to all of the time lag tool are enabled

### Requirement: Tool inputs stay disabled while a task runs

While a tool's task runs, the tool's inputs SHALL be disabled. Selecting another file SHALL NOT enable them before the
task completes.

#### Scenario: File selected during surface flattening
- **WHEN** surface flattening of a GPR file is running and the user selects another GPR file that has a ground profile
- **THEN** the inputs of the GPR elevation tool stay disabled until the flattening completes

### Requirement: No automatic regrid while gridding runs

When a time-lagged series that the current grid was built from is updated while the gridding tool is computing a grid,
the grid SHALL NOT be recomputed automatically; the warning that the grid needs to be recalculated SHALL be shown
instead and SHALL remain shown after the running gridding completes. When no gridding is running, the grid SHALL be
recomputed automatically as before.

#### Scenario: Time lag applied during gridding
- **WHEN** the grid of a time-lagged series is shown, the user starts gridding, and a time lag is applied to the same
  series before the gridding completes
- **THEN** no second gridding starts, and after the running gridding completes the grid is shown with the warning that
  it needs to be recalculated

#### Scenario: Time lag applied with no gridding running
- **WHEN** the grid of a time-lagged series is shown, no gridding is running, and a time lag is applied to the same
  series
- **THEN** the grid is recomputed automatically
