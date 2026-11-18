## ADDED Requirements

### Requirement: Grid extent around the gridded data

A grid SHALL be shown only inside the outline of the gridded data points extended outward by one cell. Data points
up to twice the blanking distance apart SHALL be joined into one outline, and the space between them SHALL be inside
it. A gap between data points wider than twice the blanking distance SHALL be outside the outline, whether it is open
to the outside of the survey or enclosed by data. The outline SHALL follow the outer survey lines and the line ends
for any survey orientation. A cell that contains a data point SHALL always be shown. No cell farther than the
blanking distance from every data point SHALL be shown.

#### Scenario: Between survey lines
- **WHEN** a survey of parallel lines with a line spacing of 4 m is gridded with a cell size of 0.8 m and a blanking
  distance of 4 m
- **THEN** the grid is shown everywhere between the lines

#### Scenario: Outer lines of a rotated survey
- **WHEN** a survey of parallel lines running at 30 degrees to north, with a line spacing of 4 m, is gridded with a
  cell size of 0.8 m and a blanking distance of 4 m
- **THEN** the grid ends one cell outside the two outer lines along their whole length, not 4 m outside them

#### Scenario: Line ends
- **WHEN** the same survey is gridded
- **THEN** the grid ends one cell beyond the line ends, along the straight edge that joins the ends of adjacent lines

#### Scenario: Gap between lines
- **WHEN** two adjacent lines of a survey are 20 m apart over their whole length and the grid is built with a
  blanking distance of 4 m
- **THEN** the grid ends one cell from each of the two lines inside the gap, for any survey orientation

#### Scenario: Area without data inside the survey
- **WHEN** a survey has an area 20 m wide without data that is surrounded by data on all sides, and the grid is built
  with a blanking distance of 4 m
- **THEN** the grid ends one cell into the area from the data around it

#### Scenario: Gap within twice the blanking distance
- **WHEN** two adjacent lines of a survey are 7 m apart and the grid is built with a cell size of 0.8 m and a
  blanking distance of 4 m
- **THEN** the grid is shown everywhere between the two lines

#### Scenario: Concave survey
- **WHEN** an L-shaped survey with a notch 50 m wide is gridded with a blanking distance of 4 m
- **THEN** the grid is not shown in the notch farther than one cell from the lines that bound it

#### Scenario: Separate survey blocks
- **WHEN** two survey blocks 200 m apart are gridded together with a blanking distance of 4 m
- **THEN** each block has its own outline and the space between the blocks is empty beyond one cell from each block

#### Scenario: Single straight line
- **WHEN** a file whose track is a single straight line is gridded with a blanking distance of 4 m and a cell size of
  0.8 m
- **THEN** the grid is shown in the cells of the line and one cell around them
