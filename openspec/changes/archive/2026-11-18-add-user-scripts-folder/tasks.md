# Tasks

## 1. Scripts folder setting and lookup

- [x] 1.1 Add `UserScriptsFolder` (setting `userScripts.path`, default `~/.geohammer/scripts`, invalid stored value →
  warning and default, `getPath()`, `synchronized setPath(@Nullable Path)`) and `UserScriptsFolderTest` covering:
  default when nothing is stored, stored folder, invalid stored value, change stored, same folder ignored, `null`
  selects the default and removes the key; run `mvn -Dtest=UserScriptsFolderTest test`
- [x] 1.2 Make `ScriptPaths` take `UserScriptsFolder` (plus a public constructor with the application folder), create
  the user folder at start, add `getUserScriptsPath()`, `setUserScriptsPath(@Nullable Path)` and
  `findScriptFile(String)`; add `ScriptPathsTest` with temporary folders covering: user folder created, user script
  found before the built-in one, built-in script found when the user folder lacks it, missing script throws,
  changed folder created and used; run `mvn -Dtest=ScriptPathsTest test`

## 2. Script list and run

- [x] 2.1 Replace `loadScriptMetadata(Path)` of `ScriptMetadataLoader` with `loadScriptMetadata()`, inject
  `ScriptPaths` into `JsonScriptMetadataLoader` (reading one folder becomes private; built-in first, user
  description replaces the one with the same file name in place, other user scripts appended, missing folder
  skipped); extend `JsonScriptMetadataLoaderTest` for these cases; run `mvn -Dtest=JsonScriptMetadataLoaderTest test`
- [x] 2.2 Use the two-folder listing in `ScriptExecutionTool` (dropping its `ScriptPaths`), `ScriptTool.findScript`
  and `ListScripts`, and
  `scriptPaths.findScriptFile` in `ScriptCoordinator.resolveScriptFile` and `GetScript`; verify the project compiles
- [x] 2.3 Make `CreateScript` write to and check "already exists" in the user scripts folder and say "user scripts
  folder" in its description; verify the project compiles

## 3. Helper modules

- [x] 3.1 Add the `CommandExecutor` overload with environment variables and run scripts in `ScriptExecutor` with
  `PYTHONPATH` from `createPythonImportFolders(scriptsPath, existing)`; verify the project compiles

## 4. Settings window

- [x] 4.1 Add the "User scripts folder" field with "Browse..." to `ScriptsTab` (load, validate as for the templates
  folder, save via `setUserScriptsPath` on the FX thread); verify the project compiles
- [x] 4.2 Show the default folder as prompt text of the scripts and templates folder fields (make
  `getDefaultPath()` of `UserScriptsFolder` and `UserTemplatesFolder` public); add a test of the default path to
  `UserScriptsFolderTest` and `UserTemplatesFolderTest`; label the fields "User scripts folder" and "User templates
  folder"; run both tests

## 5. Docs

- [x] 5.1 Update the runtime folders paragraph of `spec/overview.md` (user scripts folder setting, user script
  replaces the built-in one, scripts run with the app `scripts/` folder on `PYTHONPATH`); verify it matches the delta
  spec

## 6. Verify

- [x] 6.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 6.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
