# gridding Specification

## Purpose

Defines which grids are shown on the map for open data files after gridding with "Apply", "Apply to all" or the MCP
gridding tool, so that every file is covered by a single grid version.

## Requirements

### Requirement: One grid per file

Each open data file SHALL be covered by at most one grid: either an individual grid built from that file alone or a
shared grid built from several files of the same template. The grid shown over a file SHALL NOT depend on which file
is selected.

#### Scenario: Switching files after Apply on a file of a shared grid
- **WHEN** a shared grid is built for files A, B and C, then "Apply" builds an individual grid for B, and the user
  selects A and then B
- **THEN** the map shows the individual grid of B over B both times, and no shared grid is shown

### Requirement: Apply removes shared grids of its template

When "Apply" builds an individual grid for a file, shared grids SHALL be removed from all files of the same template.
Individual grids of other files SHALL be kept.

#### Scenario: Apply on a file of a shared grid
- **WHEN** a shared grid is built for files A, B and C, and "Apply" builds an individual grid for B
- **THEN** B shows its individual grid, and A and C have no grid

#### Scenario: Apply keeps individual grids of other files
- **WHEN** files A and B have individual grids and "Apply" builds a new individual grid for B
- **THEN** A keeps its grid and B shows the new grid

#### Scenario: Apply on a file opened after Apply to all
- **WHEN** a shared grid is built for files A and B of a template, file C of the same template is opened, and "Apply"
  builds an individual grid for C
- **THEN** C shows its individual grid, and A and B have no grid

### Requirement: Apply to all removes individual grids of its template

When "Apply to all" builds a shared grid for the files of a template, individual grids SHALL be removed from all files
of that template, and its files SHALL show the shared grid. Grids of files of other templates SHALL be kept.

#### Scenario: Apply to all after individual grids
- **WHEN** files A and B of template X have individual grids and "Apply to all" builds a shared grid for template X
- **THEN** A and B show the shared grid

#### Scenario: Apply to all keeps grids of other templates
- **WHEN** file D of template Y has a grid and "Apply to all" builds a shared grid for template X
- **THEN** D keeps its grid

### Requirement: MCP gridding follows Apply

Gridding run over MCP for a file SHALL replace grids as "Apply" does for that file.

#### Scenario: MCP gridding on a file of a shared grid
- **WHEN** a shared grid is built for files A and B, and gridding is run over MCP for A
- **THEN** A shows its new individual grid and B has no grid

### Requirement: Time-lag regrid keeps the grid kind

When a time lag is applied to the series of a grid and the grid is rebuilt automatically, a shared grid SHALL be
rebuilt as a shared grid for the files of its template, and an individual grid as an individual grid.

#### Scenario: Time lag on a file of a shared grid
- **WHEN** a shared grid of a lagged series is built for files A and B, and the time lag of A is changed
- **THEN** the shared grid is rebuilt for A and B, and both show it

### Requirement: Grids are replaced when the new grid is ready

Existing grids SHALL be replaced only when the new grid is computed. A gridding that is cancelled, fails or produces no
grid SHALL keep the existing grids.

#### Scenario: Gridding cancelled
- **WHEN** a shared grid is built for files A and B, "Apply" starts gridding for A, and the gridding is cancelled
- **THEN** A and B keep the shared grid

#### Scenario: Gridding produces no grid
- **WHEN** a shared grid is built for files A and B, and "Apply" runs gridding for A on a series without values
- **THEN** A and B keep the shared grid, and the gridding task completes without an error
