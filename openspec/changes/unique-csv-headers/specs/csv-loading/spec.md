## ADDED Requirements

### Requirement: Column names are unique after load

When a CSV or fixed-width data file is opened, the system SHALL load every column from its own position in the row
and SHALL give every column a unique name. Names are compared after trimming surrounding whitespace and are case
sensitive.

- A column whose name is not empty and was not used by a column to its left SHALL keep its name.
- A column with an empty name SHALL be named `column_<index>`, where the index is the zero-based position of the
  column in the row.
- A column whose name is already used SHALL get that name with a suffix `_N`, where N is the smallest number
  starting from 1 that gives a name not used by any other column.
- A generated name SHALL NOT be equal to a name written in the file for another column, whatever the position of
  that column.

A column mapped in the template by a repeated name is the first column with that name.

#### Scenario: Repeated name
- **WHEN** a file has the header `Time,Value,Value,Lat,Lon` and a row `10:00:00,1,2,50,20`
- **THEN** the columns are `Time`, `Value`, `Value_1`, `Lat`, `Lon`
- **AND** the row has `Value` 1, `Value_1` 2, `Lat` 50 and `Lon` 20

#### Scenario: Name repeated several times
- **WHEN** a file has the header `Lat,Lon,Mag,Mag,Mag`
- **THEN** the columns are `Lat`, `Lon`, `Mag`, `Mag_1`, `Mag_2`

#### Scenario: Empty names
- **WHEN** a file has the header `Lat,Lon,,,Mag` and a row `50,20,1,2,3`
- **THEN** the columns are `Lat`, `Lon`, `column_2`, `column_3`, `Mag`
- **AND** the row has `column_2` 1, `column_3` 2 and `Mag` 3

#### Scenario: Repeated and empty names together
- **WHEN** a file has the header `Header,Header,,`
- **THEN** the columns are `Header`, `Header_1`, `column_2`, `column_3`

#### Scenario: Suffixed name is written in the file
- **WHEN** a file has the header `Header,Header,Header_1`
- **THEN** the columns are `Header`, `Header_2`, `Header_1`
- **AND** the column `Header_1` has the values of the third cell of each row

#### Scenario: Position name is written in the file
- **WHEN** a file has the header `column_1,,Lat,Lon`
- **THEN** the columns are `column_1`, `column_1_1`, `Lat`, `Lon`
- **AND** the column `column_1` has the values of the first cell of each row

#### Scenario: Unique names
- **WHEN** a file has the header `Time,Value,value,Lat,Lon`
- **THEN** the columns keep the names written in the file

### Requirement: Unique column names are kept on save

The unique column names SHALL be part of the file data, so saving the file writes them to the header line in place
of the repeated and empty names. A saved file opened again SHALL have the same column names.

#### Scenario: Save after rename
- **WHEN** a file with the header `Header,Header,,` is opened and saved
- **THEN** the header line of the saved file is `Header,Header_1,column_2,column_3`
- **AND** every column of the saved file has the values it had in the source file

#### Scenario: Saved file opened again
- **WHEN** the saved file is opened
- **THEN** its columns are `Header`, `Header_1`, `column_2`, `column_3`

### Requirement: Renamed columns are reported

When the user opens a file and any of its columns got a name different from the one written in the file, the system
SHALL show a warning that lists the new names of those columns and states that they will be saved under the new
names. The system SHALL NOT show this warning for a file whose column names were unique and not empty.

#### Scenario: Renamed columns
- **WHEN** the user opens a file with the header `Lat,Lon,Mag,Mag,`
- **THEN** a warning names the columns `Mag_1` and `column_4` and says they will be saved under these names

#### Scenario: No renamed columns
- **WHEN** the user opens a file with the header `Lat,Lon,Mag`
- **THEN** no warning about column names is shown
