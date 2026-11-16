# csv-templates Specification

## Purpose

Defines where CSV templates are found, which template is chosen for a data file, and how user templates are kept
apart from the templates shipped with the application so that they survive reinstalls.

## Requirements

### Requirement: Templates are loaded from the user and application folders

The system SHALL load templates from the user templates folder and from the application `templates` folder. The user
templates folder SHALL be the `templates` subfolder of the user folder. The system SHALL create it at startup, and
after the user folder is changed, when it does not exist. An empty or missing folder SHALL NOT prevent templates from
the other folder from loading.

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
- **WHEN** the user folder is changed in settings to a folder without a `templates` subfolder
- **THEN** the subfolder is created and only built-in templates are loaded

### Requirement: Templates are matched in folder order

When choosing a template for a data file, the system SHALL try templates from the file's own folder first, then
templates from the user templates folder, then templates from the application folder. The first template whose match
regex matches the file SHALL be used.

#### Scenario: User template before built-in template
- **WHEN** a file matches both a user template and a built-in template with different names
- **THEN** the file is opened with the user template

#### Scenario: Template next to the file first
- **WHEN** a file matches a template in its own folder and a template in the user templates folder
- **THEN** the file is opened with the template from its own folder

#### Scenario: Built-in template as fallback
- **WHEN** a file matches no template in its own folder or in the user templates folder, and matches a built-in template
- **THEN** the file is opened with the built-in template

### Requirement: User template replaces the built-in template with the same name

A template in the user templates folder SHALL replace a template in the application folder that has the same template
name. The replaced built-in template SHALL NOT be used for matching files or for looking up templates by name, so
per-template settings apply to the user template. Built-in templates with other names SHALL stay available.

#### Scenario: Same name in both folders
- **WHEN** the user templates folder and the application folder both have a template named "MagArrow", and a file
  matches only the built-in one
- **THEN** the file is not opened with the built-in "MagArrow" template

#### Scenario: Settings follow the user template
- **WHEN** a user template replaces the built-in template with the same name
- **THEN** settings saved for that template name before apply to files opened with the user template

#### Scenario: Different template names
- **WHEN** the user templates folder has a template named "MyMag" and the application folder has "MagArrow"
- **THEN** both templates are available for matching

### Requirement: Template folder changes are picked up without a restart

The system SHALL reload templates when a template file in the user templates folder or in the application templates
folder is created, changed or deleted, and SHALL show a status message about it. After the user folder is changed in
settings, the system SHALL follow the new user templates folder instead of the previous one. Files opened after the
reload SHALL be matched against the reloaded templates. Already open files SHALL keep their template.

#### Scenario: Template deleted
- **WHEN** a user template that replaced a built-in template of the same name is deleted
- **THEN** files opened afterwards can match the built-in template again

#### Scenario: Template edited
- **WHEN** the match regex of a user template is changed in its file
- **THEN** files opened afterwards are matched with the new regex

#### Scenario: Template added after the folder was changed
- **WHEN** the user folder was changed in settings and a template file is then put into the new user templates
  folder
- **THEN** files opened afterwards can match it without restarting the application

### Requirement: Missing templates are reported at startup

The system SHALL warn the user at startup when no valid template was loaded from either the user templates folder or
the application folder.

#### Scenario: Only user templates
- **WHEN** the application folder has no templates and the user templates folder has a valid template
- **THEN** no warning about missing templates is shown

#### Scenario: No templates anywhere
- **WHEN** neither folder has a valid template
- **THEN** a warning about missing templates is shown
