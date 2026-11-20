# Spec Delta

## MODIFIED Requirements

### Requirement: Templates folder is shown and changed in settings

The "Templates" tab of the settings window SHALL show the user templates folder and SHALL let the user change it by
typing a path or choosing a folder. The default folder SHALL be `.geohammer/templates` in the user's home directory.
When the settings are saved with another folder, the system SHALL use it without a restart and in later sessions. An
empty value SHALL select the default folder, and an empty field SHALL show the default folder as a hint.

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

#### Scenario: Empty field hints the default folder
- **WHEN** the templates folder field is empty
- **THEN** it shows the default folder as a hint

#### Scenario: No templates folder on the General tab
- **WHEN** the user opens the "General" tab of the settings window
- **THEN** it has no field for a user or templates folder
