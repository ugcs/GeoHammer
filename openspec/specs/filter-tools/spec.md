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

### Requirement: Tool task keeps the target it was started with

A tool's task SHALL process the file, series and input values that were selected when the user started the action.
Apply to all SHALL process the files of the selected file's template that were open when the user started it.
Selecting another file, another series or a file of another template while the task runs SHALL NOT change which files
or series the task processes or the values it uses. A file opened while the task runs SHALL NOT be processed by it.

#### Scenario: File of another template selected during Apply to all
- **WHEN** a low-pass Apply to all runs on several files of template X and the user selects a file of template Y
  before it completes
- **THEN** every open file of template X is filtered, and no file of template Y is filtered or marked unsaved

#### Scenario: Another series selected during a filter
- **WHEN** a running median Apply runs on series A of a file and the user selects series B of the same file before it
  completes
- **THEN** only series A is filtered

#### Scenario: Another file selected during quality control
- **WHEN** quality control Apply runs on file A and the user selects file B before it completes
- **THEN** the quality issues shown are those of file A

#### Scenario: Selection cleared during quality control
- **WHEN** quality control Apply runs on a file and the user closes all files before the task reads its target
- **THEN** the check runs for the file selected at start and completes without an error

#### Scenario: File opened during Apply to all
- **WHEN** a time lag Apply to all runs on the files of template X and a file of template X is opened before it
  completes
- **THEN** the opened file is not time-lagged

#### Scenario: Another file selected during gridding
- **WHEN** gridding runs on file A and the user selects file B before it completes
- **THEN** the grid of file A is shown with the render filter that was set when gridding started, and the render filter
  of file B is unchanged

### Requirement: Tool preferences are saved for the template at start

When the user starts a tool action, the tool's input values SHALL be saved as the preferences of the template of the
file selected at that moment. Selecting a file of another template right after starting the action SHALL NOT save the
values under the other template or change its saved values.

#### Scenario: File of another template selected right after Apply
- **WHEN** the user applies a time lag with shift 5 to a file of template X, whose saved shift was 3, and immediately
  selects a file of template Y whose saved shift is 7
- **THEN** the saved shift of template X is 5 and the saved shift of template Y stays 7
