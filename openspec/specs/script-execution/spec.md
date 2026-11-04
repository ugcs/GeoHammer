# script-execution Specification

## Purpose

Defines which values a Python script receives when it runs on open files, how GeoHammer resolves them for each
processed file, and which of them users and agents may set.

## Requirements

### Requirement: Script inputs are declared in the script description

A script SHALL receive, besides the path of the temporary copy of the processed file, only the parameters declared in
its JSON description. GeoHammer SHALL NOT pass column names or other values to scripts through environment variables.

#### Scenario: Script without declared parameters
- **WHEN** a script declares no parameters and runs on a file
- **THEN** the script receives only the path of the copy of that file

#### Scenario: No column environment variables
- **WHEN** a script runs on a CSV file whose template defines a latitude column
- **THEN** the script environment contains no variable carrying that column name

### Requirement: Hidden parameters are not set by users or agents

A parameter declared with `"visible": false` SHALL NOT be shown in the script panel, its value SHALL NOT be stored
between runs, it SHALL NOT be listed by the MCP script tools, and a run request that sets it SHALL be rejected. Its
value SHALL be resolved by GeoHammer for each processed file.

#### Scenario: Hidden parameter in the panel
- **WHEN** the user selects a script with a hidden parameter in the script panel
- **THEN** no input is shown for that parameter

#### Scenario: Agent sets a hidden parameter
- **WHEN** an agent runs a script through MCP and passes a value for a hidden parameter
- **THEN** the run is rejected with an error naming the parameter and the script does not start

#### Scenario: Hidden parameter with a default value
- **WHEN** a hidden parameter is neither a semantic column nor an original path and declares a default value
- **THEN** the script receives the default value on every run

### Requirement: Parameter visibility defaults

A parameter without the `visible` field SHALL be hidden when it is a semantic column or an original path parameter and
visible otherwise.

#### Scenario: Regular parameter without the flag
- **WHEN** a DOUBLE parameter omits `visible`
- **THEN** it is shown in the script panel

#### Scenario: Semantic column without the flag
- **WHEN** a COLUMN_NAME parameter declares a semantic and omits `visible`
- **THEN** it is not shown in the script panel

### Requirement: Semantic column parameters

A COLUMN_NAME parameter that declares a semantic (Line, Latitude, Longitude, Altitude, Altitude AGL, TMI, Mark) SHALL
receive, for each processed file, the header of the column that has this semantic in that file. When the file has no
such column, the parameter SHALL receive the semantic name itself.

#### Scenario: Template names the column differently
- **WHEN** a script with a hidden Latitude column parameter runs on a file whose template maps latitude to column `lat`
- **THEN** the script receives `lat` for that parameter

#### Scenario: Several files with different templates
- **WHEN** a script with a hidden Latitude column parameter runs on all files, one mapping latitude to `lat` and one to
  `Latitude`
- **THEN** the first run receives `lat` and the second receives `Latitude`

#### Scenario: File without the semantic column
- **WHEN** a script with a Latitude column parameter runs on a file that has no latitude column
- **THEN** the script receives `Latitude` for that parameter

### Requirement: Visible semantic columns can be overridden

When a semantic column parameter is visible, the script panel SHALL preselect the column with that semantic in the
selected file, and the user or agent SHALL be able to choose another column. A value chosen by the user or agent SHALL be passed
as given; when no value is given, the value SHALL be resolved per processed file.

#### Scenario: Preselected column
- **WHEN** the user selects a script with a visible Latitude column parameter for a file mapping latitude to `lat`
- **THEN** the parameter input shows `lat` selected

#### Scenario: Agent omits a visible semantic column
- **WHEN** an agent runs the script through MCP without a value for a visible Latitude column parameter
- **THEN** the script receives the latitude column of the processed file

### Requirement: Original path parameter

A parameter of type ORIGINAL_PATH SHALL receive, for each processed file, the absolute path of the source file that
is open in GeoHammer, not the path of its temporary copy. An ORIGINAL_PATH parameter SHALL always be hidden.

#### Scenario: Original path of a processed file
- **WHEN** a script with an original path parameter runs on `/data/survey/flight_01.sgy`
- **THEN** the script receives `/data/survey/flight_01.sgy` for that parameter

#### Scenario: Original path declared visible
- **WHEN** a script description declares an ORIGINAL_PATH parameter with `"visible": true`
- **THEN** the script is reported as having invalid metadata and cannot be run

### Requirement: Output files are named after the source file

Bundled scripts that create output files SHALL name them after the source file, keeping its full name stem.

#### Scenario: Source name with a timestamp
- **WHEN** the RSS processing script runs on `survey_20261009123456.sgy`
- **THEN** the created CSV is named `survey_20261009123456_processed.csv`

### Requirement: RSS results are written next to the source file

The RSS processing script SHALL write its CSV to the folder of the source SGY file and SHALL NOT ask for an output
folder.

#### Scenario: RSS processing run
- **WHEN** the user runs the RSS processing script on `/data/rss/line_3.sgy`
- **THEN** the script panel shows no output folder input and the CSV is written to `/data/rss/line_3_processed.csv`

### Requirement: Script list includes scripts added while running

When the user opens the script dropdown in the script panel, the dropdown SHALL list the scripts present in the
`scripts` folder at that moment, filtered by the selected file's template and the current operating system as before.
No restart and no change of the selected file SHALL be needed to see a newly added script.

#### Scenario: Script copied into the folder
- **WHEN** the user copies `my_filter.py` and `my_filter.json` into the `scripts` folder while GeoHammer runs and then
  opens the script dropdown
- **THEN** the dropdown lists `my_filter`

#### Scenario: Script created by an agent
- **WHEN** an agent creates a script through MCP `create_script` for the template of the selected file and the user
  then opens the script dropdown
- **THEN** the dropdown lists the new script

#### Scenario: Script removed from the folder
- **WHEN** a script's files are deleted from the `scripts` folder and the user opens the script dropdown
- **THEN** the dropdown no longer lists that script

### Requirement: Reloading the script list keeps the current selection

Reloading the script list SHALL keep the selected script selected while it is still present in the `scripts` folder,
and SHALL keep the values entered in its parameter inputs. When the selected script is no longer present, the
selection SHALL be cleared.

#### Scenario: Entered values survive opening the dropdown
- **WHEN** the user selects a script, changes one of its parameter values and opens the script dropdown again without
  choosing another script
- **THEN** the same script stays selected and the changed value is still shown

#### Scenario: Selected script deleted
- **WHEN** the selected script's files are deleted from the `scripts` folder and the user opens the script dropdown
- **THEN** no script is selected and no parameter inputs are shown

### Requirement: Unreadable script description does not hide other scripts

A script whose JSON description cannot be read SHALL be left out of the scripts offered in the script panel and by
the MCP script tools, and SHALL be logged as a warning naming the file. The other scripts SHALL stay available, and
no error dialog SHALL be shown for it.

#### Scenario: Malformed description in the folder
- **WHEN** `broken.json` in the `scripts` folder is not valid JSON and the user opens the script dropdown
- **THEN** the dropdown lists every other script for the selected file and no error dialog is shown

#### Scenario: Agent lists scripts
- **WHEN** an agent calls MCP `list_scripts` while `broken.json` is not valid JSON
- **THEN** the call succeeds and returns every other script

### Requirement: Bundled CSV scripts reject input without the header on the first line

A bundled script that reads its input as a CSV table SHALL check the first lines of the file before reading it. When
the first line is empty or starts with `%` or `#`, when it has no separator while the next line has, or when its
values look like data instead of column names, the script SHALL stop with an error that names the file and the
reason, and SHALL NOT change the file.

#### Scenario: Marker line before the header
- **WHEN** the user runs "Export as Geosoft (Oasis montaj) database" on a MagDrone ASC RTR file whose first line is
  `Raw track data`
- **THEN** the script fails with an error saying that a line precedes the header, instead of "no numeric columns to
  export"

#### Scenario: Comment lines before the header
- **WHEN** the user runs data decimation on a MagDrone ASC file that starts with `%` comment lines
- **THEN** the script fails with an error saying that comment lines precede the header, and the file data is unchanged

#### Scenario: Header in a comment line
- **WHEN** the user runs data decimation on an RTKLib position file
- **THEN** the script fails with an error saying that comment lines precede the header

#### Scenario: No header line
- **WHEN** the user runs data decimation on a comma-separated file whose first line holds numbers
- **THEN** the script fails with an error saying that the file has no header line

#### Scenario: File with the header on the first line
- **WHEN** the user runs data decimation on a file whose first line is its header
- **THEN** the script reads and decimates it as before

### Requirement: Geosoft export reads the separator of the file

The Geosoft export SHALL read the file with the separator used in it, so that files separated by tabs or semicolons
are exported with all their numeric columns.

#### Scenario: Tab-separated file
- **WHEN** the user exports a tab-separated MagDrone ASC file with the header on the first line to a Geosoft database
- **THEN** the database contains a channel for every numeric column of the file

### Requirement: Generated requirements exclude modules local to the script

When GeoHammer generates a requirements file for a script, the file SHALL NOT list a module that is a Python file or a
package in the script's directory. Module and package names SHALL be compared after PyPI name normalization (case
and the separators `-`, `_`, `.` are ignored). Other detected packages SHALL be kept.

#### Scenario: Script imports the bundled helper module
- **WHEN** a script that imports `script_utils` and `pandas` has no requirements file and `script_utils.py` lies in
  the script's directory
- **THEN** the generated requirements file lists `pandas` and does not list `script_utils` or `script-utils`
- **AND** installing the script's dependencies does not install the PyPI package `script-utils`

#### Scenario: Local package directory
- **WHEN** a script imports a module that is a package directory next to the script
- **THEN** the generated requirements file does not list that module

#### Scenario: Name differs only by separator or case
- **WHEN** the analyzer reports `Script-Utils` and `script_utils.py` lies in the script's directory
- **THEN** the generated requirements file does not list it

### Requirement: Dependency reinstall reuses the existing requirements file

When the user confirms a forced reinstall of a script's dependencies, GeoHammer SHALL reinstall from the script's
existing requirements file and SHALL NOT regenerate or modify that file. A requirements file SHALL be generated only
when the script has none.

#### Scenario: Bundled script with a requirements file
- **WHEN** the user confirms reinstalling dependencies for a bundled script that has a requirements file
- **THEN** the packages listed in that file are reinstalled and the file content is unchanged

#### Scenario: Script without a requirements file
- **WHEN** the user confirms reinstalling dependencies for a script that has no requirements file
- **THEN** a requirements file is generated for the script and its packages are reinstalled
