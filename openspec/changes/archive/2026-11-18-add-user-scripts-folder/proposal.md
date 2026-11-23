# Proposal

## Why

Python scripts are read only from the application `scripts/` folder. A reinstall replaces that folder, so scripts
users add there, and scripts agents create with `create_script`, are lost. This is stage 2 of #1078: scripts get a
user folder outside the app install, with its own setting, as templates did in stage 1.

## What Changes

- The "Scripts" tab of the settings window gets a "User scripts folder" field: the folder user scripts are read from,
  `~/.geohammer/scripts` by default. The app creates the folder when it is missing.
- The script panel and the MCP script tools offer scripts from the application folder and from the user scripts
  folder. Built-in scripts are listed first; scripts found only in the user folder follow.
- A user script replaces the built-in script with the same file name: it is listed once, in the built-in script's
  place, and its file is the one that runs and is returned to agents.
- Scripts run with the application `scripts` folder on the Python module path, so user scripts can import the
  shipped helper module `script_utils`. A module next to the script still takes precedence.
- MCP `create_script` writes to the user scripts folder; the name of a built-in script is allowed and replaces it.
- Both folder fields show the default folder as a hint when empty; the templates field on the "Templates" tab is
  labeled "User templates folder".

## Non-goals

- Leaving modules of the application `scripts` folder out of requirements files generated for user scripts. A
  generated file may list `script_utils`; this only matters when packages have to be installed. Deferred to a
  separate change.
- The `create_script` description still suggests `detect_separator` instead of `read_input_csv`; fixing that is a
  separate change.
- Moving scripts from the application folder, or from a previous user scripts folder, when the setting changes.
- Watching the scripts folder: the script list is already read again each time the dropdown opens.
- A shared folder setting for templates and scripts.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `script-execution`: scripts are found in the user scripts folder and the application folder, a user script
  replaces the built-in script with the same file name, the scripts folder is shown and changed on the "Scripts"
  tab, user scripts can import the shipped helper module, and `create_script` writes to the user folder.
- `csv-templates`: an empty templates folder field shows the default folder as a hint, as the scripts folder field
  does. The field is labeled "User templates folder".

## Impact

- Code: new `UserScriptsFolder` (setting); `ScriptPaths` (user folder, script lookup); `ScriptMetadataLoader` /
  `JsonScriptMetadataLoader` (two folders); `ScriptExecutionTool`, MCP `ScriptTool`, `ListScripts`, `GetScript`,
  `CreateScript`; `ScriptCoordinator`; `CommandExecutor` and `ScriptExecutor` (`PYTHONPATH`); `ScriptsTab`;
  `TemplatesTab` and `UserTemplatesFolder` (default folder hint, field label).
- Settings: new key for the user scripts folder in `templates-settings.properties`, absent while the default is used.
- Specs: `spec/overview.md` (runtime folders).
