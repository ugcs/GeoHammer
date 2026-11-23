## 1. Demo files in place

- [x] 1.1 Remove the `DemoFiles` bean and move its logic into `DemoFile`, renamed to `DemoSurvey`: `getFile()` (the
  shipped file, `NoSuchFileException` when missing) replaces `createCopy`, the static `isDemoFile` matching the
  shipped demo files replaces `isInDemoFolder`. Verify with `mvn compile`.
- [x] 1.2 Use `isDemoFile` in `FileOpenEventsAnalytics`.
- [x] 1.3 Remove `DemoService`: open the shipped file from `EmptyProfileView` with `Loader.load` on the FX thread and
  stop disabling the demo links while a demo opens.


## 2. Saving

- [x] 2.1 In `Saver`, disable Save for demo files, skip demo files in Save all and reject saving a demo file in place
  (CSV Save to over the demo file itself).
- [x] 2.2 In `Saver`, start the Save to file and folder dialogs of a demo file without a folder.
- [x] 2.3 Re-activate the `ProfileView` tool buttons on `FileRenameEvent`.

## 3. Other outputs

- [x] 3.1 Default the script folder parameter (`ScriptExecutionTool`) to empty for a demo file.
- [x] 3.2 Default the projection export path (`ExportView`) to empty for a demo file.

## 4. Tests and specs

- [x] 4.1 Rewrite `DemoFilesTest` as `DemoSurveyTest` for `getFile` and `isDemoFile` on the shipped `data/` files.
  Verify with `mvn -Dtest=DemoSurveyTest test`.
- [x] 4.2 Update `spec/overview.md`: `~/.geohammer` no longer holds demo copies.

## 5. Verify

- [x] 5.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings.
- [x] 5.2 Invoke the `code-review` skill on the uncommitted changes with this proposal and specs as the completeness
  reference, fix critical and major issues, and report the scores and any issues left open.
