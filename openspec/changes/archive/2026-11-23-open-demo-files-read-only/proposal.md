## Why

A demo copies the shipped demo file and its sidecar into `~/.geohammer/demo` on every click, only so that saving a demo
never writes into the installation and every demo starts from the original data. Blocking in-place saving of the
shipped demo files gives the same guarantees without the copy, the extra disk use in the user folder and the code that
wipes a folder in the user profile (#1106).

## What Changes

- A demo opens the demo file shipped with the app in place; nothing is copied into the user folder.
- Save is disabled for a demo file, and Save all skips demo files. Save to stays available, and for a CSV demo it
  continues the work in the saved file as before.
- Outputs that default to the folder of the selected file (Save to dialogs, script output folder, projection export
  path) have no default for a demo file instead of the installation folder: the user chooses where results go.
- A demo file is one of the shipped demo files, also when opened with Open or by drag and drop; other files are never
  demo files, even in a folder named like the demo folder. The analytics demo flag follows the same rule.

## Non-goals

- A true Save as for GPR, sonar and NMEA files: Save to keeps exporting a copy while the open file stays the demo file.
- Deleting the `~/.geohammer/demo` folder left by earlier versions.
- Blocking the geotagger from writing positions into a demo file the user adds to it.

## Capabilities

### New Capabilities

### Modified Capabilities
- `welcome-screen`: a demo opens the shipped file, which cannot be saved in place, the same way Open opens a file.
- `usage-analytics`: the demo flag marks the shipped demo files.
- `file-saving`: "Save to.." of a demo file to its own path is refused instead of saving in place.

## Impact

- `DemoFiles` bean removed; its logic moves into the `DemoFile` enum, renamed to `DemoSurvey` (a shipped demo survey
  with its title and file), so callers check a file without injecting a bean: `DemoSurvey.getFile()` returns the
  shipped file (or throws `NoSuchFileException`), the static `DemoSurvey.isDemoFile` (for a `File` or an `SgyFile`)
  replaces `isInDemoFolder` and matches the shipped demo files by path. A folder check is not enough: the demo
  folder is resolved from the working directory first, so user files in a `data` folder there would become read-only.
- `DemoService` removed: it only moved the copy off the FX thread. `EmptyProfileView` opens the shipped file with
  `Loader.load` on the FX thread like Open does; the demo links are no longer disabled while a demo opens.
- `Saver`: Save activation policy and Save all skip demo files; saving a demo file in place is rejected; Save to dialogs
  start without a folder for demo files.
- `ScriptExecutionTool` (folder parameter default) and `ExportView` (default export path) leave the value empty for demo
  files. Each caller branches on `DemoSurvey.isDemoFile`; `DemoSurvey` does not decide output folders of other files.
- `ProfileView`: tool buttons are re-activated on `FileRenameEvent`, so Save is enabled after a CSV demo is saved to a
  new file.
- `FileOpenEventsAnalytics` uses `isDemoFile`.
- No persisted format, settings or dependency changes.
