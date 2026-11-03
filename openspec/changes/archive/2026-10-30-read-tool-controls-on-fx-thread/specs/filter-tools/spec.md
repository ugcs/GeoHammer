## MODIFIED Requirements

### Requirement: Tool task keeps the target it was started with

A tool's task SHALL process the file, series and input values that were selected when the user started the action.
Apply to all SHALL process the files of the selected file's template that were open when the user started it.
Selecting another file, another series or a file of another template while the task runs SHALL NOT change which files
or series the task processes or the values it uses. A file opened while the task runs SHALL NOT be processed by it.
A ground surface rebuild of the GPR elevation tool SHALL use the elevation input values shown when the user changed an
input.

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

#### Scenario: Elevation input changed
- **WHEN** the user changes the sample offset of a GPR file with a ground profile
- **THEN** the ground surface of that file is rebuilt with the new sample offset and the trace header, altitude header,
  trace lag, peak detection, peak window and filter window shown at that moment

#### Scenario: Elevation inputs changed in quick succession
- **WHEN** the user changes the trace lag of a GPR file to 10 and then to 12 before the first rebuild completes
- **THEN** after the rebuilds complete, the ground surface of that file is built with trace lag 12

#### Scenario: Flatten surface without air gap removal
- **WHEN** the user clears "Remove air gap" and starts surface flattening of a GPR file with a ground profile
- **THEN** the file is flattened and no air gap samples are removed

## ADDED Requirements

### Requirement: Selecting a GPR file keeps its ground profile

When the user selects a GPR file, the GPR elevation tool SHALL show that file's ground profile settings. Selecting the
file SHALL NOT change the settings of its ground profile or rebuild its ground surface.

#### Scenario: Files with different ground profile settings
- **WHEN** GPR file A has trace lag 5 and peak detection on, GPR file B has trace lag 0 and peak detection off, A is
  selected, and the user selects B
- **THEN** the GPR elevation tool shows trace lag 0 and peak detection off, and the ground profile and surface of B are
  unchanged

#### Scenario: Return to the previous file
- **WHEN** the user selects GPR file B after file A with different ground profile settings, and then selects A again
- **THEN** the GPR elevation tool shows the ground profile settings of A, and the ground profile and surface of A are
  unchanged

### Requirement: GPR chart shows only complete ground surfaces

While the ground surface of a GPR file is rebuilt, the GPR chart SHALL show the previous complete surface until the new
surface is complete.

#### Scenario: Filter window dragged
- **WHEN** peak detection is on and the user drags the filter window slider of a GPR file
- **THEN** the profile line on the chart shows only surfaces filtered with a window the slider passed, never raw
  altitudes, unfiltered peaks or a line at the top of the chart
