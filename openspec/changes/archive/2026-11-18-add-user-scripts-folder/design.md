# Design

## Context

`ScriptPaths` (bean) resolves one folder, the application `scripts/`: the working directory first, then the folder
next to the jar. It is used three ways:

- **Listing.** `scriptMetadataLoader.loadScriptMetadata(scriptPaths.getScriptsPath())` reads every `*.json` on each
  call, in directory order. Callers: `ScriptExecutionTool.loadScriptsFor` (each time the dropdown opens),
  `ScriptTool.findScript` (base of `get_script` and `run_script`) and `ListScripts`. `ScriptMetadataLoader` has one
  implementation, `JsonScriptMetadataLoader`.
- **Locating the file.** `ScriptCoordinator.resolveScriptFile` and `GetScript` resolve `metadata.filename()` against
  the folder. `ScriptMetadata` does not know its folder.
- **Writing.** `CreateScript` writes `<name>.py` and `<name>.json` into the folder.

`check_imports.py` and `script_utils.py` (imported by 8 built-in scripts) live in the application folder. Python
finds `script_utils` only because it lies next to the running script. `check_imports.py` puts the script's folder on
its path and runs from the application folder, so it finds `script_utils` for any script.

`CommandExecutor` starts processes with the inherited environment and no way to add variables. `ScriptExecutor`
builds the command and runs it on the executor thread of `ScriptCoordinator`.

Stage 1 of #1078 added `UserTemplatesFolder`: a bean that only stores the templates folder setting (default,
invalid stored value, removing the key for the default); `FileTemplates` applies a change. Settings tabs run `load`,
`validate` and `save` on the FX thread; `TemplatesTab` applies the folder on the shared `ExecutorService`.

The change touches no file data: snapshots, undo, `setUnsaved` and cache invalidation are not involved.

## Goals / Non-Goals

**Goals:**
- The folder order and the "user script replaces built-in" rule are written once: the list in the loader, the file
  in `ScriptPaths`.
- The same setting structure as templates, so both folders read alike.

**Non-Goals:**
- Recording the source folder in `ScriptMetadata`.
- A shared base class for `UserTemplatesFolder` and `UserScriptsFolder`; extract one only if a third folder setting
  appears.

## Decisions

### `UserScriptsFolder` stores the setting

New bean `com.ugcs.geohammer.service.script.UserScriptsFolder`, built like `UserTemplatesFolder`:

- `UserScriptsFolder(Settings)` reads group `userScripts`, key `path` into a `volatile Path`. Empty means the default,
  `~/.geohammer/scripts`; an invalid path is logged as a warning and replaced by the default.
- `getPath()`; `synchronized setPath(@Nullable Path)`: `null` selects the default, the current folder does nothing,
  otherwise it stores the setting (removes the key for the default) and switches the field.
- Its own constants for the default folder names; no file IO, no events.

### `ScriptPaths` adds the user folder and the file lookup

- Spring constructor `ScriptPaths(UserScriptsFolder)` (annotated `@Autowired`) delegating to a public
  `ScriptPaths(UserScriptsFolder, Path scriptsPath)` for tests. The constructor creates the user scripts folder; a
  failure is logged.
- `getScriptsPath()` keeps meaning the application folder (`check_imports.py`, helper modules).
- `getUserScriptsPath()` returns `userScriptsFolder.getPath()`.
- `setUserScriptsPath(@Nullable Path)` passes the path to `UserScriptsFolder` and creates the folder when
  it changed. No event: every reader asks `ScriptPaths` on each call, and nothing caches the list.
- `findScriptFile(String filename)` returns the file from the user scripts folder when it is there, else from the
  application folder; it throws `IOException` naming the file when neither has it.

### The loader merges the two folders

`ScriptMetadataLoader.loadScriptMetadata(Path scriptsDir)` is replaced by `loadScriptMetadata()`.
`JsonScriptMetadataLoader` gets `ScriptPaths` injected and takes both folders from it:

```
list = descriptions of getScriptsPath() (built-in)   // missing folder -> empty
for each description of getUserScriptsPath():        // missing folder -> empty
    same filename in list -> replace it in place
    otherwise             -> append
```

Reading one folder becomes private to `JsonScriptMetadataLoader`, so no caller can list scripts without the user
folder. `ScriptPaths` says which folders there are; the loader reads and merges them.
`ScriptExecutionTool.loadScriptsFor`, `ScriptTool.findScript` and `ListScripts` call `loadScriptMetadata()`;
`ScriptExecutionTool` no longer needs `ScriptPaths`. `ScriptCoordinator.resolveScriptFile` and `GetScript` call
`scriptPaths.findScriptFile(metadata.filename())`.

*Alternative:* pass both folders, or `ScriptPaths`, to `loadScriptMetadata`. Rejected: every caller would repeat
which folders to read and in what order.

*Alternative:* `ScriptPaths.getScriptsPaths()` returning both folders and a loader taking a list with "first wins".
Rejected: it fixes the lookup order but not the list order (built-in first), so the order rule would be split.

*Alternative:* merge in each caller. Rejected: three copies of the rule.

### Helper modules through `PYTHONPATH`

- `CommandExecutor` gets an overload `executeCommand(command, workingDirectory, environment, onOutput)` that adds the
  variables to the process environment; the existing overloads delegate with an empty map.
- `ScriptExecutor` gets `ScriptPaths` and runs the script with `PYTHONPATH` = absolute application scripts folder,
  followed by `File.pathSeparator` and the existing `PYTHONPATH` when it is set. The value is built by
  `createPythonImportFolders(Path scriptsPath, @Nullable String existing)`.
- Python puts the script's own folder first, so a module next to the script wins; built-in scripts are unaffected.
- `ScriptImports`, `PackageInstaller` and `ScriptRequirements` are unchanged.

*Alternative:* copy `script_utils.py` into the user folder. Rejected: the app would write and overwrite files in the
user's folder, and a stale copy would replace the shipped module.

### `create_script` writes to the user folder

`CreateScript` uses `scriptPaths.getUserScriptsPath()` for both files and for the "already exists" check, so a
built-in script name is accepted and replaces it. Its description says "user scripts folder".

### Settings window

`ScriptsTab` gets `ScriptPaths` and a "User scripts folder" row below the Python path,
built like the field of `TemplatesTab`: text field, "Browse..." with a `DirectoryChooser` opened at the current folder.
`load` shows `getUserScriptsPath()`; `validate` rejects an invalid path and a path that is not an existing directory
(empty and current folder pass; the path is made absolute and normalized); `save` keeps storing the Python path and
calls `setUserScriptsPath` (`null` for an empty field), logging a failure.

`save` applies the folder on the FX thread, unlike `TemplatesTab`: a change only stores the setting and creates the
folder, which `validate` has just found on the same thread, and nothing is reloaded. Running it on the executor would
let a script list opened right after saving read the previous folder.

An empty field selects the default folder, which the field alone does not tell. Both `ScriptsTab` and `TemplatesTab`
set the default folder as the field's prompt text; `UserScriptsFolder.getDefaultPath()` and
`UserTemplatesFolder.getDefaultPath()` become public for that. The templates field is relabeled "User templates
folder" to match.

## Risks / Trade-offs

- [Requirements file generated for a user script lists `script_utils`] `pipreqs` sees the import, and the exclusion
  of local modules only covers the script's own folder. Harmless while packages are installed: the import check
  finds `script_utils` and pip is not run. When packages must be installed, pip tries `script_utils` from PyPI and
  may fail the whole install. → Deferred by decision; separate change. A shipped `_requirements.txt` copied with the
  script avoids it.
- [Description and file from different folders] The description is the user one when the user folder has the
  `.json`, the file is the user one when it has the `.py`. A user folder with only one of the two pairs it with the
  built-in other half. → Accepted: both are written together by `create_script` and copied together by users.
- [Shadowing] The application folder is on `PYTHONPATH` ahead of installed packages, so a `.py` there with a package
  name would hide that package for every script. → Only uniquely named scripts, `script_utils` and `check_imports`
  are there.
- [Stale user copy of a built-in script] It keeps replacing the updated built-in script. → Chosen behavior, as for
  templates; delete the copy to get the built-in one.
- [Scripts created by agents before this version] They stay in the application folder and can be lost on reinstall.
  → Not moved, as for templates.

## Migration Plan

No data migration: the user scripts folder starts empty and built-in scripts work as before. Rollback is a plain
revert; the settings key and the folder are ignored by older versions.
