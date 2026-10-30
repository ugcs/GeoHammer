## ADDED Requirements

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
