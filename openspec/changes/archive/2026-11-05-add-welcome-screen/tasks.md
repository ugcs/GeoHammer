# Tasks

## 1. Demo files and packaging

- [x] 1.1 Get the MagNIMBUS `2023-07-06-12-38-33-quspin.csv` from the "2023-07-06 MagNIMBUS single-sensor.zip" dataset,
  confirm with the user its size and that it may be redistributed, and check it opens with the `SPH_MagNIMBUS`
  template; done when the user has confirmed
- [x] 1.2 Add the demo files as `demo/gpr/demo-gpr.sgy` and `demo/csv/demo-csv.csv` to the repository
  unchanged; verify file sizes match the sources
- [x] 1.3 Add `demo/` to the Windows `stage-installer-input` resources and a macOS `copy-demo` execution next to
  `copy-scripts` in `pom.xml`, and a `demo` fileSet to `assembly/linux/zip.xml`; verify each entry mirrors the
  `scripts` one and `mvn clean package` still succeeds

## 2. Demo copy service

- [x] 2.1 Add the `DemoFile` enum (`GPR`, `CSV` with demo file paths) and `DemoFiles` that resolves `demo/` via
  `Resources.resolvePath`, replaces `~/.geohammer/demo/<demo>/` with a fresh copy of the demo file and returns the copy;
  missing demo file or IO failure propagates as `IOException`
- [x] 2.2 Unit tests for `DemoFiles` with a temp demo folder and temp user folder: copy is created, a second call
  removes a leftover sidecar and edited copy and restores the original content, a missing demo file throws; verify with
  `mvn -Dtest=DemoFilesTest test`

## 3. Welcome panel

- [x] 3.1 Add `EmptyProfileView`: framed drop hint, demo columns halfway between it and the links at the bottom, GPR and sensor columns with extension tags from
  `FileTypes.getSupportedExtensions()` (new, unmodifiable, with unit tests in `FileTypesTest`), demo buttons with captions, wiki links via `Views.browse`; styles in the components stylesheet for both
  themes; the two columns stay in one row
- [x] 3.2 Demo buttons: `DemoService.openDemo` copies on the shared executor and starts `loader.load` on the FX
  thread; the view disables both buttons and re-enables them when the returned future completes; on failure log and show a `Dialogs` error naming the demo
- [x] 3.3 Drop zone highlight: the panel fills the chart area under the toolbar and toggles a pseudo-class on drag
  entered/exited/dropped when the dragboard has files, without consuming the events so `SceneContent` still opens dropped files
- [x] 3.4 Place `EmptyProfileView` over the charts scroll area in `ProfileView`, move the visibility logic on
  `FileOpenedEvent` / `FileClosedEvent` into it and remove the `openHint` code from `SceneContent`
- [x] 3.5 Update `spec/overview.md`: add `demo/` to runtime folders and `~/.geohammer/demo/` as the demo copy location

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
