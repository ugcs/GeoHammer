## 1. Demo flag

- [x] 1.1 Remove `EventType.DEMO_FILE_OPENED`, `EventsFactory.createDemoFileOpenedEvent` and the event sending in
  `DemoService`. Verify with `mvn compile`.
- [x] 1.2 Add `DemoFiles.isInDemoFolder(File)` and pass it as the `demo` property of the file opened and file open
  error events in `FileOpenEventsAnalytics`. Verify with `mvn compile`.
- [x] 1.3 Add `DemoFilesTest` cases for `isInDemoFolder`: a created copy, a non-normalized path to it, the shipped
  source and a file in a subfolder. Verify with `mvn -Dtest=DemoFilesTest test`.

## 2. Verify

- [x] 2.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings.
- [ ] 2.2 Invoke the `code-review` skill on the uncommitted changes with this proposal and spec as the completeness
  reference, fix critical and major issues, and report the scores and any issues left open.
