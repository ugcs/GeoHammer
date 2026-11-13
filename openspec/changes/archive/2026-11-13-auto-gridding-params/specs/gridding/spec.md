## ADDED Requirements

### Requirement: Estimated gridding parameters

Gridding parameters SHALL be estimated from the line spacing of the selected file: the cell size SHALL be a fifth of
the line spacing and the blanking distance SHALL be equal to the line spacing. Both values SHALL be rounded to 2
decimal places and limited to the range accepted by the gridding inputs: at least 0.01 m and at most the input's upper
limit (100 m). No parameters SHALL be estimated when the line spacing is not estimated.

#### Scenario: Parameters from the line spacing
- **WHEN** the estimated line spacing of the selected file is 4 m
- **THEN** the estimated cell size is 0.8 m and the estimated blanking distance is 4 m

#### Scenario: Rounding
- **WHEN** the estimated line spacing of the selected file is 2.47 m
- **THEN** the estimated cell size is 0.49 m and the estimated blanking distance is 2.47 m

#### Scenario: Wide line spacing
- **WHEN** the estimated line spacing of the selected file is 150 m
- **THEN** the estimated cell size is 30 m and the estimated blanking distance is 100 m

#### Scenario: Narrow line spacing
- **WHEN** the estimated line spacing of the selected file is 0.02 m
- **THEN** the estimated cell size is 0.01 m and the estimated blanking distance is 0.02 m

### Requirement: Auto button

The gridding tool SHALL show an "Auto" button in the row of the cell size and blanking distance inputs. Pressing it
SHALL fill both inputs with the parameters estimated from the selected file only. It SHALL NOT run gridding and SHALL
NOT save the parameters; they are used and saved as if the user typed them. When the selected file has a grid built
with other parameters, the warning that the grid needs to be recalculated SHALL be shown. When no parameters are
estimated, the inputs SHALL keep their values and the tool SHALL show a warning that no survey lines were detected,
which disappears by itself after a few seconds; no dialog SHALL be shown.

#### Scenario: Auto fills the inputs
- **WHEN** the user presses "Auto" for a file with an estimated line spacing of 4 m
- **THEN** the cell size input shows 0.8 and the blanking distance input shows 4, and no gridding is run

#### Scenario: Apply to all after Auto
- **WHEN** the user presses "Auto" for file A and then "Apply to all" for the files A and B of a template
- **THEN** the shared grid of A and B is built with the parameters estimated from A

#### Scenario: Auto on a file with a grid
- **WHEN** the selected file has a grid and "Auto" fills parameters that differ from those of the grid
- **THEN** the warning that the grid needs to be recalculated is shown

#### Scenario: No survey lines detected
- **WHEN** the user presses "Auto" for a file whose track is a single straight line
- **THEN** the inputs keep their values, a warning that no survey lines were detected is shown and then hidden
  after a few seconds, and no dialog is shown

### Requirement: Estimated template defaults

When a data file is selected, has no grid, and its template has no saved gridding parameters, the gridding tool SHALL
estimate the parameters from that file, show them in the inputs and save them as the parameters of the template. When
no parameters are estimated, the inputs SHALL show a cell size of 0.1 m and a blanking distance of 1 m, nothing SHALL
be saved and no warning SHALL be shown. A file with a grid SHALL show the parameters of its grid, and a template with
saved parameters SHALL show the saved parameters, without an estimate.

#### Scenario: First file of a template
- **WHEN** a file of a template without saved gridding parameters is selected and its estimated line spacing is 4 m
- **THEN** the inputs show a cell size of 0.8 and a blanking distance of 4, and these values are saved for the
  template

#### Scenario: Next file of the same template
- **WHEN** the estimated parameters were saved for a template from file A and file B of the same template is selected
- **THEN** the inputs show the parameters saved from A and no estimate is made for B

#### Scenario: Template with saved parameters
- **WHEN** a file is selected whose template has saved gridding parameters
- **THEN** the inputs show the saved parameters

#### Scenario: Selection changes during the estimate
- **WHEN** a file of a template without saved parameters is selected and another file is selected before its estimate
  completes
- **THEN** the estimate of the first file is neither shown nor saved

#### Scenario: No estimate for the first file of a template
- **WHEN** a file of a template without saved gridding parameters is selected and its track is a single straight line
- **THEN** the inputs show a cell size of 0.1 and a blanking distance of 1, nothing is saved for the template, and
  the next selected file of the template is estimated

### Requirement: Saving gridding parameters

Gridding parameters SHALL be saved for the template of the selected file when "Apply" or "Apply to all" runs gridding
and when estimated parameters become the template defaults. Changing the range, palette, color scale or a
post-processing option SHALL NOT save the gridding parameters.

#### Scenario: Palette change keeps saved parameters
- **WHEN** a template has no saved gridding parameters, the user types a cell size, changes the palette, and selects
  another file of the template without running gridding
- **THEN** the template still has no saved gridding parameters typed by the user

#### Scenario: Apply saves parameters
- **WHEN** the user types a cell size of 2 and presses "Apply"
- **THEN** a cell size of 2 is saved for the template of the selected file

### Requirement: Recalculation warning only for a file with a grid

The warning that the grid needs to be recalculated SHALL be shown only when the selected file has a grid. Opening,
selecting or changing a file without a grid SHALL NOT show the warning, not even for a moment.

#### Scenario: File opened while the gridding tool is shown
- **WHEN** a data file is opened while the gridding tool is expanded and no grid was built for the file
- **THEN** no warning is shown in the gridding tool

#### Scenario: Data of a gridded file changes
- **WHEN** the selected file has a grid and its lines are cropped
- **THEN** the warning that the grid needs to be recalculated is shown

### Requirement: MCP estimate of gridding parameters

An MCP tool SHALL return the estimated cell size, blanking distance and line spacing of a data file without running
gridding. The line spacing SHALL be returned as estimated, without rounding. The tool SHALL NOT change the file, its
grid or the saved gridding parameters. It SHALL report an error when no parameters are estimated.

#### Scenario: Estimate over MCP
- **WHEN** the MCP estimate tool is called for a file with an estimated line spacing of 4 m
- **THEN** it returns a cell size of 0.8 m, a blanking distance of 4 m and a line spacing of 4 m, and the file and
  its grid are unchanged

#### Scenario: No estimate over MCP
- **WHEN** the MCP estimate tool is called for a file whose track is a single straight line
- **THEN** it reports an error that no survey lines were detected
