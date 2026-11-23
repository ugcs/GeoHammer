# Spec Delta

## ADDED Requirements

### Requirement: Templates folder is shown and changed in settings

The "Templates" tab of the settings window SHALL show the user templates folder and SHALL let the user change it by
typing a path or choosing a folder. The default folder SHALL be `.geohammer/templates` in the user's home directory.
When the settings are saved with another folder, the system SHALL use it without a restart and in later sessions. An
empty value SHALL select the default folder.

#### Scenario: Default folder shown
- **WHEN** the user opens the "Templates" tab and has never changed the templates folder
- **THEN** the field shows the `.geohammer/templates` folder in the user's home directory

#### Scenario: Folder changed
- **WHEN** the user chooses another existing folder that has a valid template and saves the settings
- **THEN** files opened afterwards can match that template, and templates of the previous folder are no longer used

#### Scenario: Folder kept between sessions
- **WHEN** the user changed the templates folder, saved the settings and restarted the application
- **THEN** user templates are read from the chosen folder and the "Templates" tab shows it

#### Scenario: Empty value
- **WHEN** the user clears the field and saves the settings
- **THEN** the default folder is used

#### Scenario: No templates folder on the General tab
- **WHEN** the user opens the "General" tab of the settings window
- **THEN** it has no field for a user or templates folder

### Requirement: Invalid templates folder is rejected

The settings window SHALL reject a new templates folder that is not an existing folder: an error SHALL be shown and
the settings SHALL NOT be saved. A stored templates folder that is not a valid path SHALL NOT prevent the application
from starting: the default folder SHALL be used.

#### Scenario: Folder does not exist
- **WHEN** the user enters a path that is not an existing folder and saves the settings
- **THEN** an error is shown and the templates folder is not changed

#### Scenario: Unreadable stored value
- **WHEN** the stored templates folder is not a valid path on this computer and the application starts
- **THEN** the application starts and uses the default folder

### Requirement: Files of a previous templates folder stay in place

Changing the templates folder SHALL NOT move or delete files of the previous folder, the settings file or demo file
copies.

#### Scenario: Files of the previous folder
- **WHEN** the user changes the templates folder
- **THEN** the files in the previous folder are left as they are and are no longer used

## MODIFIED Requirements

### Requirement: Templates are loaded from the user and application folders

The system SHALL load templates from the user templates folder and from the application `templates` folder. The user
templates folder SHALL be the folder chosen in settings. The system SHALL create it at startup when it does not
exist. An empty or missing folder SHALL NOT prevent templates from the other folder from loading.

#### Scenario: Template in the user folder
- **WHEN** a valid template is in the user templates folder and a CSV file matching its match regex is opened
- **THEN** the file is opened with that template

#### Scenario: User templates folder created at startup
- **WHEN** the application starts and the user templates folder does not exist
- **THEN** the folder exists after startup and the built-in templates are loaded

#### Scenario: Template survives a reinstall
- **WHEN** a template is in the user templates folder and the application folder is replaced by a reinstall
- **THEN** the template is still loaded after the next start

#### Scenario: User folder changed
- **WHEN** the templates folder is changed in settings to an existing folder without templates
- **THEN** only built-in templates are loaded

### Requirement: Template folder changes are picked up without a restart

The system SHALL reload templates when a template file in the user templates folder or in the application templates
folder is created, changed or deleted, and SHALL show a status message about it. After the templates folder is changed
in settings, the system SHALL follow the new user templates folder instead of the previous one. Files opened after the
reload SHALL be matched against the reloaded templates. Already open files SHALL keep their template.

#### Scenario: Template deleted
- **WHEN** a user template that replaced a built-in template of the same name is deleted
- **THEN** files opened afterwards can match the built-in template again

#### Scenario: Template edited
- **WHEN** the match regex of a user template is changed in its file
- **THEN** files opened afterwards are matched with the new regex

#### Scenario: Template added after the folder was changed
- **WHEN** the templates folder was changed in settings and a template file is then put into the new folder
- **THEN** files opened afterwards can match it without restarting the application
