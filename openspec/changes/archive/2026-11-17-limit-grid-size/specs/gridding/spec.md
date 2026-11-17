## ADDED Requirements

### Requirement: Grid size limit

Gridding SHALL be rejected when the grid would hold more cells than an internal limit. The limit is 50 million cells;
it is not a setting and can change in a later version. The rejection SHALL be decided before the grid is built. A
rejected gridding SHALL produce no grid and SHALL keep the existing grids. The limit SHALL apply to "Apply", "Apply
to all", the automatic rebuild after a time lag and the MCP gridding tool. The wording of the rejection message is
not specified.

#### Scenario: Grid within the limit
- **WHEN** a survey of 500 m by 500 m is gridded with a cell size of 0.1 m
- **THEN** the grid is built

#### Scenario: Cell size too small for the survey
- **WHEN** a survey of 1 km by 1 km is gridded with a cell size of 0.1 m
- **THEN** the gridding is rejected and no grid is built

#### Scenario: Row with invalid coordinates
- **WHEN** a file of a survey at latitude 50 has one row at latitude 0 and longitude 0, and it is gridded with a cell
  size of 0.1 m
- **THEN** the gridding is rejected at once and the app stays responsive

#### Scenario: Existing grid is kept
- **WHEN** a file has a grid and a gridding of the file is rejected
- **THEN** the file keeps its grid on the map

#### Scenario: Apply to all
- **WHEN** "Apply to all" runs for files A and B of a template whose combined extent holds more than 50 million
  cells of the cell size
- **THEN** the gridding is rejected and A and B keep their grids

### Requirement: Rejection shown in the gridding tool

When a gridding started from the gridding tool is rejected, the tool SHALL show the rejection message in its warning
banner and SHALL NOT show a dialog. The banner SHALL NOT disappear by itself. It SHALL stay until the user changes
the cell size or the blanking distance, selects another file or series, or another gridding succeeds; changing the
range, palette, color scale or a post-processing option SHALL keep it. After that the banner SHALL show the warning
that the grid needs to be recalculated when the selected file has a grid built with other parameters, and SHALL be
hidden otherwise. The cell size and blanking distance inputs SHALL keep the values typed by the user.

#### Scenario: Rejection stays visible
- **WHEN** the user presses "Apply" with a cell size that exceeds the limit and waits
- **THEN** the banner shows the rejection message and is still shown a minute later, and no dialog is shown

#### Scenario: Cell size changed after a rejection
- **WHEN** a gridding of a file without a grid is rejected and the user types another cell size
- **THEN** the banner is hidden

#### Scenario: Rejection on a file with a grid
- **WHEN** a file has a grid built with a cell size of 1 m, a gridding with a cell size of 0.01 m is rejected, and
  the user types a cell size of 0.5 m
- **THEN** the banner shows the rejection message until the user types, and then the warning that the grid needs to
  be recalculated

#### Scenario: Another file selected after a rejection
- **WHEN** a gridding of file A is rejected and the user selects file B of the same template without a grid
- **THEN** the banner is hidden

#### Scenario: Palette changed after a rejection
- **WHEN** a gridding is rejected and the user changes the palette
- **THEN** the banner still shows the rejection message

#### Scenario: Inputs are kept
- **WHEN** the user types a cell size of 0.01 and the gridding is rejected
- **THEN** the cell size input still shows 0.01

#### Scenario: Inputs are kept on a file with a grid
- **WHEN** a file has a grid built with a cell size of 1 m, the user types a cell size of 0.01 and the gridding is
  rejected
- **THEN** the cell size input still shows 0.01, not 1

### Requirement: Rejection over MCP

When a gridding run over MCP is rejected by the grid size limit, the tool SHALL report an error, no grid SHALL be
built and the file SHALL keep its existing grid. The description of the MCP gridding tool SHALL state that a grid
above an internal limit is rejected.

#### Scenario: Very small cell size over MCP
- **WHEN** gridding is run over MCP for a survey of 1 km by 1 km with a cell size of 0.001 m
- **THEN** the tool reports an error and the file keeps its grid
