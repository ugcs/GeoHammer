# Tasks

## 1. Modal window

- [x] 1.1 In `GeotaggerView.showWindow`, set `initOwner(AppContext.stage)` and `Modality.APPLICATION_MODAL` when
  creating the stage; verify it compiles and the stage is created only once per show
- [x] 1.2 In `ProfileView`, drop the `geotaggerStage` field and the close-request override; the Geotagging button
  just calls `geotaggerView.showWindow()`, which becomes `void`; verify no other caller uses the returned stage
  (`grep showWindow`)

## 2. Cooperative cancellation in Geotagger

- [x] 2.1 Add an `AtomicBoolean cancelRequested` parameter to `Geotagger.geotag`; check it before each data file and throw
  `CancellationException` when set (design, decision 4); verify the existing callers compile and the rest of the
  file loop is unchanged
- 2.2 Skipped (decided by the user): unit tests in `GeotaggerTest` for files not open in GeoHammer. `Geotagger`
  needs a real `Model`, whose `FileManager` constructor is package-private in `model`, and the tests may use neither
  mocks nor reflection. Planned cases, for when the constructor becomes accessible: cancelled before start writes
  nothing and throws `CancellationException`; cancelled from the progress callback during the second file leaves
  the first and second files saved with new positions and the third file unchanged on disk; not cancelled saves all
  files.

## 3. Close handling in GeotaggerView

- [x] 3.1 Route the Close button and the window close request through one handler: idle
  closes as today; while processing it consumes the request, sets `cancelRequested`, disables Close and shows
  "Cancelling..."; verify by reading that no path calls `closeWindow()` while the task runs
- [x] 3.2 In `processFiles()`, reset `cancelRequested`, pass `List.copyOf` of both lists, and on task end (FX thread) hide
  the progress, report `"Processing cancelled"` on `CancellationException`, and close the
  window if close was requested; verify it compiles and the success and failure messages are unchanged

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
