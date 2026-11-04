# Spec Delta

## ADDED Requirements

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
