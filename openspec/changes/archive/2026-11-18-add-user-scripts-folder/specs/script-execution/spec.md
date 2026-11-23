# Spec Delta

## ADDED Requirements

### Requirement: Scripts are found in the user and application folders

The system SHALL offer scripts from the application `scripts` folder and from the user scripts folder, in the script
panel and in the MCP script tools. The user scripts folder SHALL be the folder chosen in settings. The system SHALL
create it at startup when it does not exist. An empty or missing folder SHALL NOT prevent scripts of the other folder
from being offered.

#### Scenario: Script in the user folder
- **WHEN** `my_filter.py` and `my_filter.json` are in the user scripts folder and a file of a matching template is
  selected
- **THEN** the script dropdown lists `my_filter` and running it executes the file from the user scripts folder

#### Scenario: User scripts folder created at startup
- **WHEN** the application starts and the user scripts folder does not exist
- **THEN** the folder exists after startup and the built-in scripts are offered

#### Scenario: Script survives a reinstall
- **WHEN** a script is in the user scripts folder and the application folder is replaced by a reinstall
- **THEN** the script is still offered after the next start

### Requirement: Built-in scripts are listed before user scripts

The script panel and MCP `list_scripts` SHALL list built-in scripts first and then scripts found only in the user
scripts folder.

#### Scenario: Order of the list
- **WHEN** the user scripts folder has `my_filter` and the application folder has built-in scripts
- **THEN** `my_filter` is listed after all built-in scripts

### Requirement: User script replaces the built-in script with the same file name

A script in the user scripts folder SHALL replace the built-in script with the same script file name. The system
SHALL list such a script once, in the place of the built-in script, with the description from the user scripts
folder, and SHALL run and return to agents the file from the user scripts folder. Built-in scripts with other file
names SHALL stay available.

#### Scenario: Same file name in both folders
- **WHEN** the user scripts folder and the application folder both have `data_decimation.py` with its description
- **THEN** the script is listed once, in the place of the built-in script, and running it executes the file from the
  user scripts folder

#### Scenario: Agent reads a replaced script
- **WHEN** an agent requests the source of a script that exists in both folders
- **THEN** the source from the user scripts folder is returned

#### Scenario: User copy deleted
- **WHEN** the user copy of a built-in script is deleted and the user opens the script dropdown
- **THEN** the built-in script is listed and runs again

### Requirement: Scripts folder is shown and changed in settings

The "Scripts" tab of the settings window SHALL show the user scripts folder and SHALL let the user change it by
typing a path or choosing a folder. The default folder SHALL be `.geohammer/scripts` in the user's home directory.
When the settings are saved with another folder, the system SHALL use it without a restart and in later sessions. An
empty value SHALL select the default folder, and an empty field SHALL show the default folder as a hint.

#### Scenario: Default folder shown
- **WHEN** the user opens the "Scripts" tab and has never changed the scripts folder
- **THEN** the field shows the `.geohammer/scripts` folder in the user's home directory

#### Scenario: Folder changed
- **WHEN** the user chooses another existing folder that has a script, saves the settings and opens the script
  dropdown
- **THEN** the dropdown lists that script and no longer lists scripts of the previous folder

#### Scenario: Folder kept between sessions
- **WHEN** the user changed the scripts folder, saved the settings and restarted the application
- **THEN** user scripts are read from the chosen folder and the "Scripts" tab shows it

#### Scenario: Empty value
- **WHEN** the user clears the field and saves the settings
- **THEN** the default folder is used

#### Scenario: Empty field hints the default folder
- **WHEN** the scripts folder field is empty
- **THEN** it shows the default folder as a hint

### Requirement: Invalid scripts folder is rejected

The settings window SHALL reject a new scripts folder that is not an existing folder: an error SHALL be shown and the
settings SHALL NOT be saved. A stored scripts folder that is not a valid path SHALL NOT prevent the application from
starting: the default folder SHALL be used.

#### Scenario: Folder does not exist
- **WHEN** the user enters a path that is not an existing folder and saves the settings
- **THEN** an error is shown and the scripts folder is not changed

#### Scenario: Unreadable stored value
- **WHEN** the stored scripts folder is not a valid path on this computer and the application starts
- **THEN** the application starts and uses the default folder

### Requirement: Files of a previous scripts folder stay in place

Changing the scripts folder SHALL NOT move or delete files of the previous folder or of the application folder.

#### Scenario: Files of the previous folder
- **WHEN** the user changes the scripts folder
- **THEN** the files in the previous folder are left as they are and are no longer offered

### Requirement: User scripts can import the shipped helper modules

A script run from the user scripts folder SHALL be able to import the Python modules shipped in the application
`scripts` folder. A module next to the script SHALL take precedence over a shipped module of the same name.

#### Scenario: Copy of a built-in script
- **WHEN** a built-in script that imports `script_utils` is copied to the user scripts folder without
  `script_utils.py` and is run
- **THEN** the script runs and uses the shipped `script_utils`

#### Scenario: Own module of the same name
- **WHEN** the user scripts folder has its own `script_utils.py` next to the script and the script is run
- **THEN** the script uses the module from the user scripts folder

### Requirement: Created scripts are saved to the user scripts folder

A script created through MCP `create_script` SHALL be written to the user scripts folder. Creating a script with the
file name of a built-in script SHALL be allowed and SHALL replace that built-in script. A script that already exists
in the user scripts folder SHALL be overwritten only when overwriting is requested.

#### Scenario: Agent creates a script
- **WHEN** an agent creates `my_filter` through `create_script`
- **THEN** `my_filter.py` and `my_filter.json` are in the user scripts folder and the application `scripts` folder is
  unchanged

#### Scenario: Name of a built-in script
- **WHEN** an agent creates a script with the file name of a built-in script and no script of that name is in the
  user scripts folder
- **THEN** the script is created in the user scripts folder and replaces the built-in one

#### Scenario: Script exists in the user folder
- **WHEN** an agent creates a script whose name is already used in the user scripts folder without requesting
  overwrite
- **THEN** the script is not changed and an error says it already exists

## MODIFIED Requirements

### Requirement: Script list includes scripts added while running

When the user opens the script dropdown in the script panel, the dropdown SHALL list the scripts present in the
application `scripts` folder and in the user scripts folder at that moment, filtered by the selected file's template
and the current operating system as before. No restart and no change of the selected file SHALL be needed to see a
newly added script.

#### Scenario: Script copied into the folder
- **WHEN** the user copies `my_filter.py` and `my_filter.json` into the user scripts folder while GeoHammer runs and
  then opens the script dropdown
- **THEN** the dropdown lists `my_filter`

#### Scenario: Script created by an agent
- **WHEN** an agent creates a script through MCP `create_script` for the template of the selected file and the user
  then opens the script dropdown
- **THEN** the dropdown lists the new script

#### Scenario: Script removed from the folder
- **WHEN** a script's files are deleted from the folder they were in and the user opens the script dropdown
- **THEN** the dropdown no longer lists that script

### Requirement: Reloading the script list keeps the current selection

Reloading the script list SHALL keep the selected script selected while it is still present in one of the scripts
folders, and SHALL keep the values entered in its parameter inputs. When the selected script is no longer present,
the selection SHALL be cleared.

#### Scenario: Entered values survive opening the dropdown
- **WHEN** the user selects a script, changes one of its parameter values and opens the script dropdown again without
  choosing another script
- **THEN** the same script stays selected and the changed value is still shown

#### Scenario: Selected script deleted
- **WHEN** the selected script's files are deleted from the folder they were in and the user opens the script
  dropdown
- **THEN** no script is selected and no parameter inputs are shown
