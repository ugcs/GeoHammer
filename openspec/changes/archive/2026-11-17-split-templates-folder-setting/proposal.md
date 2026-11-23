# Proposal

## Why

Stage 1 of #1078 added one "User folder" setting on the "General" tab, with templates in its `templates` subfolder,
so that scripts could later share it. Users find a folder per kind of file clearer: the field points straight at the
folder that holds the files, and its default reads `~/.geohammer/templates`, as the task asked. Templates get their
own setting and their own "Templates" tab now; scripts get their own folder in a separate change.

## What Changes

- The settings window gets a "Templates" tab with a "Templates folder" field: the folder user templates are loaded
  from, `~/.geohammer/templates` by default. The field accepts a path or a folder chosen with "Browse...".
- The "User folder" field is removed from the "General" tab, and the shared user folder setting is dropped.
- With the default settings nothing changes for users: templates are loaded from the same folder as before.
- A stored user folder is not carried over. The setting was never released (stage 1 exists only on the #1078
  branch), so no migration is needed.
- Loading, matching, override by name and watching of user templates stay as they are.

## Non-goals

- Scripts (stage 2 of #1078). They get their own folder setting on the "Scripts" tab in a separate change.
- Moving files from a previous templates folder when the setting changes.
- The template editor: "Save as" keeps opening next to the data file.
- Template files users put into the application folder.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `csv-templates`: the user templates folder is its own setting, shown and changed on the "Templates" tab, with
  `~/.geohammer/templates` as the default; an invalid folder is rejected.
- `user-folder`: retired. Its requirements are replaced by the templates folder requirements in `csv-templates`.

## Impact

- Code: `FileTemplates` (owns the templates folder setting), new `TemplatesTab`, `GeneralTab` (field removed),
  `SettingsView` (new tab); `UserFolder` and `UserFolderChangedEvent` are deleted.
- Settings: the `userFolder.path` key is replaced by a templates folder key in `templates-settings.properties`. It is
  absent while the default folder is used.
- Specs: `spec/overview.md` (runtime folders, events, CSV templates recipe); main specs `csv-templates` and
  `user-folder`.
