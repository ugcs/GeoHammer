# Proposal

## Why

CSV templates live in the application `templates/` folder. A reinstall overwrites that folder, so templates users add
there are lost. Users need a folder outside the app install that survives updates, and a way to choose where it is
(#1078). This is stage 1 of #1078. Scripts follow in stage 2 and use the same folder.

## What Changes

- The app gets a user folder: `~/.geohammer` by default. It is the single place for files users add; this change
  puts templates there, the scripts change adds scripts.
- The "General" tab of the settings window shows the user folder and lets the user change it. A change applies when
  the settings are saved, without a restart, and is kept between sessions.
- Templates are loaded from the `templates` subfolder of the user folder as well as from the application
  `templates/` folder. The app creates the subfolder when it is missing.
- Lookup order when matching a file: the file's own folder, then the user templates folder, then the application
  folder. The first `match-regex` hit wins, as now.
- A user template replaces the built-in template with the same template name, both for matching and for lookups by
  name (per-template settings, series selection).
- Changes to the user templates folder are picked up without a restart, as changes to the application folder are
  now.

## Non-goals

- Scripts (`scripts/`). They are stage 2 of #1078.
- Template files users put into, or edit in, the application folder. They load as before and are not moved or
  protected from a reinstall. This is a separate task.
- The template editor. "Save as" keeps opening next to the data file, and saving into the application folder stays
  rejected.
- Moving files from the old user folder when the setting changes.
- Moving the settings file, demo copies or analytics properties. They stay in `~/.geohammer` whatever the user
  folder is.
- Separate folders for templates and scripts.

## Capabilities

### New Capabilities

- `user-folder`: the folder for files users add to the app, its default, and how it is shown and changed in
  settings.
- `csv-templates`: where CSV templates are found, the order they are matched in, how user templates replace built-in
  ones, and how template folder changes are picked up.

### Modified Capabilities

None.

## Impact

- Code: new `UserFolder` (owner of the setting) and `UserFolderChangedEvent`; `FileTemplates` (user templates folder,
  merging, watcher, reaction to a folder change); `GeneralTab` (folder field).
- Settings: new key for the user folder in `templates-settings.properties`. It is absent while the default folder is
  used.
- Specs: `spec/overview.md` (runtime folders, events, CSV templates recipe).
