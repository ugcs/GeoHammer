# Tasks

## 1. Revert the in-place reload

- [x] 1.1 Remove `SgyFile.reloadFrom` with `reloadAuxElementsFrom`, `reloadMetaFrom`, `reloadTracesFrom` and all
  implementations, and their tests in `CsvFileTest`, `GprFileTest`, `DztFileTest`, `NmeaFileTest`, `SonarFileTest`;
  verify that `mvn test` passes
- [x] 1.2 Remove `SonarFile.SnapshotWithPackets`, `NmeaFile.SnapshotWithSentences` and the position file restore in
  `TraceFile.SnapshotWithTraces`, with their tests; verify that `git diff HEAD -- src/main/java/com/ugcs/geohammer/format
  src/test/java/com/ugcs/geohammer/format` is empty
- [x] 1.3 Rename `loadResultFrom` back to `loadFrom` in `Loader`, `CsvFile`, `TraceFile`, `SonarFile`, `SgyFileWithMeta`
  and `ScriptExecutor`; verify that `ScriptExecutor` has no diff against HEAD and `loadResultFrom` is not found

## 2. Reopen flow

- [x] 2.1 Make the `open*File` methods return the opened instance (type detection moves to `readFile`) and queue the
  chart in the caller, with no template parameter; verify by running `mvn test` and opening CSV, SEG-Y, DZT,
  svlog and NMEA files in the app
- [x] 2.2 Add `Dialogs.confirm`, a confirmation awaited from a background thread (pattern of
  `TemplateEditor.createTemplate`); verify manually that Keep, Reload and closing the dialog return the expected answer
- [x] 2.3 Add `Model.replaceFile(openFile, reloadedFile, version)`: skip when the version changed, remove and dispose the
  old chart, publish `FileClosedEvent`, add the fresh chart at the same index without re-centering the map, select it; verify manually that reopening the middle of three charts
  keeps their order and that Undo after a reload does not change the file
- [x] 2.4 Implement the reopen flow in `Loader.openFile` after meta resolution: version read before the unsaved check,
  unsaved + non-interactive keeps the file with a status message, unsaved + interactive asks before reading, the
  loader waits for `replaceFile` on the FX thread, a skipped reload is reported; a reopened file returns
  `Result.success` and is listed in `FileOpenedEvent` whatever the outcome; verify
  manually against every scenario of `specs/file-opening/spec.md`
- [x] 2.5 Turn the recreate branch of `Model.initChart` into a guard that selects the existing chart; verify manually
  that two quick drops of the same file give one chart

The reopen flow lives in `Loader` and `Model`, which need the Spring context and the FX thread; it is verified by the
manual checks above instead of unit tests.

## 3. Docs and integration

- [x] 3.1 Update the "Reopening" entry and the `FileOpenedEvent` note in `spec/overview.md` to the close-and-open flow;
  verify the text matches the implemented flow
- [x] 3.2 Run `mvn test` and check the reopen scenarios for each file type, including Undo after Reload, chart order,
  a folder with a modified file and a meta (`.geohammer`) file of an open GPR file
