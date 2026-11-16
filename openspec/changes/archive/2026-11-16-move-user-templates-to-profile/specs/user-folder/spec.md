# Spec Delta

## Purpose

Defines the single folder where users keep the files they add to the application, so that these files survive
reinstalls and their location can be chosen in one place.

## ADDED Requirements

### Requirement: User folder has a default location

The system SHALL keep files users add to the application under one user folder. The user folder SHALL be `.geohammer`
in the user's home directory unless the user has chosen another one. Each kind of user files SHALL have its own
subfolder of the user folder.

#### Scenario: Default folder
- **WHEN** the user has never changed the user folder
- **THEN** user templates are read from `.geohammer/templates` in the user's home directory

#### Scenario: Chosen folder
- **WHEN** the user folder is set to `/data/survey`
- **THEN** user templates are read from `/data/survey/templates`

### Requirement: User folder is shown and changed in settings

The "General" tab of the settings window SHALL show the current user folder and SHALL let the user change it by
typing a path or choosing a folder. When the settings are saved with another folder, the system SHALL use that folder
without a restart and in later sessions. An empty value SHALL select the default folder.

#### Scenario: Default folder shown
- **WHEN** the user opens the settings window and has never changed the user folder
- **THEN** the field shows the `.geohammer` folder in the user's home directory

#### Scenario: Folder changed
- **WHEN** the user chooses another existing folder whose `templates` subfolder has a valid template and saves the
  settings
- **THEN** files opened afterwards can match that template, and templates of the previous user folder are no longer
  used

#### Scenario: Folder kept between sessions
- **WHEN** the user changed the folder, saved the settings and restarted the application
- **THEN** user files are read from the chosen folder and the settings window shows it

#### Scenario: Empty value
- **WHEN** the user clears the field and saves the settings
- **THEN** the default folder is used

### Requirement: Invalid user folder is rejected

The settings window SHALL reject a new user folder that is not an existing folder: an error SHALL be shown and the
settings SHALL NOT be saved. A stored user folder that is not a valid path SHALL NOT prevent the application from
starting: the default folder SHALL be used.

#### Scenario: Folder does not exist
- **WHEN** the user enters a path that is not an existing folder and saves the settings
- **THEN** an error is shown and the user folder is not changed

#### Scenario: Unreadable stored value
- **WHEN** the stored user folder is not a valid path on this computer and the application starts
- **THEN** the application starts and uses the default folder

### Requirement: Other application files stay in place

Changing the user folder SHALL NOT move the settings file, demo file copies or any files of the previous user
folder.

#### Scenario: Files of the previous folder
- **WHEN** the user changes the user folder
- **THEN** the files in the previous user folder are left as they are and are no longer used
