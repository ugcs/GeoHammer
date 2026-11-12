## 1. Demo data

- [x] 1.1 Replace `data/demo-gpr.sgy` and add its sidecar `data/demo-gpr.sgy.geohammer`.
- [x] 1.2 Rename `DemoFile.GPR` to "GPR Grid Survey Demo".
- [x] 1.3 Replace `data/demo-csv.csv` with the newer MagNIMBUS survey and check that it opens with the MagNIMBUS
  template.

## 2. Sidecar copy

- [x] 2.1 In `DemoFiles.createCopy`, copy the sidecar of the source (`MetaFiles.getMetaPath`) next to the copy when it
  exists. Verify with `mvn compile`.
- [x] 2.2 Add `DemoFilesTest` cases: the sidecar is copied, no sidecar is created without a source sidecar, and an
  edited sidecar copy is replaced. Verify with `mvn -Dtest=DemoFilesTest test`.

## 3. Demo file opening event

- [x] 3.1 Add `EventType.DEMO_FILE_OPENED` and `EventsFactory.createDemoFileOpenedEvent`; send
  the event from `DemoService.openDemo` when the loader result of the demo copy is a success. Verify with
  `mvn compile`.

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings.
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes with this proposal and spec as the completeness
  reference, fix critical and major issues, and report the scores and any issues left open.
