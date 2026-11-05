# Design

## Context

- `SceneContent` already stacks a mouse-transparent `openHint` label over `ProfileView.getCenter()` in the chart pane
  and toggles it on `FileOpenedEvent` / `FileClosedEvent` by `FileManager.getFilesCount()`. The welcome panel replaces
  this label.
- Drag and drop is handled once on the `SceneContent` root (`Loader.getDragHandler()` / `getDropHandler()`), so drops on
  any child node, including a clickable panel, already open files.
- Runtime folders `templates/` and `scripts/` are resolved by `Resources.resolvePath` (working dir, then next to the
  jar) and packaged per platform: `pom.xml` `windows-build` (`stage-installer-input` resources), `macos-build`
  (`copy-templates` / `copy-scripts` executions into the installer input) and `assembly/linux/zip.xml`.
- Opening a GPR file creates a meta sidecar `<name>.geohammer` next to it on save; CSV files are rewritten in place. The
  installation folder is not writable on Windows and must not be modified inside the signed macOS app bundle.
- Wiki links already open via `Views.browse(url)`.

## Goals / Non-Goals

**Goals:**
- One self-contained view class for the panel, including its visibility and drop highlight.
- Demo files reuse the existing load path (`Loader.load`), with no format-specific code.

**Non-Goals:**
- A generic demo catalog: two demos are described by a small enum, not by a config file.

## Decisions

### Welcome panel fills the chart area under the toolbar

New `EmptyProfileView` (`@Component`, root package next to `SceneContent`) builds the panel in its constructor: framed open
hint, two format columns below it in one row with demo buttons, link row at the bottom. `ProfileView.getCenter()` stacks
it over the charts scroll area below the toolbar (`StackPane` that grows to the remaining height), so the whole chart
area is the drop zone and the toolbar (geotagger button) stays usable. The previous scroll area children keep their
own VBox, so chart sizing is unchanged. The `openHint` label in `SceneContent` is removed.

Three equal growing spacers sit above the hint, between the hint and the columns and between the columns and the
links, so the links stay at the bottom and the columns stay halfway between the drop zone and the links. Spacings
between the columns and between the extension tags are set in the components stylesheet (`empty-profile-columns`,
`empty-profile-formats`).

`EmptyProfileView` sets its own visibility on `FileOpenedEvent` / `FileClosedEvent` (`getFilesCount() == 0`, read inside
`Platform.runLater` so it does not depend on listener order). Collapsed charts still count as open files, which
matches the spec. Drag and drop events bubble to the `SceneContent` handlers unchanged.

Alternative: put the panel inside `Model.getChartsContainer()`. Rejected: the container is owned by `Model` and its
children are chart roots indexed by position.

### Drop highlight via drag events on the panel

`EmptyProfileView` observes `DRAG_ENTERED` / `DRAG_EXITED` / `DRAG_DROPPED` on itself (not consuming them) and toggles a
pseudo-class that highlights the dashed frame around the drop hint. Since the panel fills the chart area, this equals
"over the chart area". The frame is as wide as the format columns and encloses only the hint, so the demo buttons and
links do not read as part of the drop zone; drops are still accepted anywhere on the window. Styling lives in the components stylesheet and uses color variables defined by every theme.

### No Open files button on the panel

The panel does not repeat the Open files toolbar button: the toolbar stays visible above the panel, and the panel is
about dropping files. `Saver` is not changed.

### Supported formats come from `FileTypes`

The view reads `FileTypes.getSupportedExtensions()` (an unmodifiable view of the supported set, in its rank order)
and puts an extension into the GPR column when the existing `isGprFile`/`isDztFile` probes match a file with that
extension, otherwise into the sensor column. So a new extension shows on the panel without touching the view. One
small bordered label (`empty-profile-format`) per extension in a centered `FlowPane`. Format names are not shown:
`FileTypes` has none, and a name list in the view would duplicate the formats again.
Sensor template names are not listed; MagNIMBUS appears in the CSV demo caption and the Templates link leads to the
template list. Alternatives: names with an extension line per group, or one line per format. Rejected as less compact.
Reading names from loaded templates is rejected too: template file names are not user-facing and the list is long.

### Demo files: shipped folder, fresh user copy

- Repository folder `demo/` holds the full demo files, committed directly (no LFS, no CI download):
  - `demo/gpr/demo-gpr.sgy` (Gas pipelines `2019-07-23-14-00-54-gpr.sgy`, 48 MB)
  - `demo/csv/demo-csv.csv` (MagNIMBUS single sensor `2023-07-06-12-38-33-quspin.csv`, 31 MB)
- `DemoFile` enum (`GPR`, `CSV`) holds the relative demo file path. `DemoFiles` (`@Component`) resolves `demo/` with
  `Resources.resolvePath`, and for a demo: deletes `~/.geohammer/demo/<gpr|csv>/`, copies the demo file there and returns
  the copy. Deleting the folder also removes the meta sidecar and any other leftovers of the previous run.
- `DemoService.openDemo(demoFile)` (`@Service`) owns the opening flow and returns a `CompletableFuture`: it creates the
  copy on the shared virtual-thread executor, starts `Loader.load` on the FX thread (its task runner creates a
  `Stage`) and waits for loading in background. Copying stays in `DemoFiles` so it is unit-tested without `Loader`.
- `EmptyProfileView` only disables both demo buttons, calls `openDemo` and re-enables them on the FX thread when the future
  completes.
- A missing demo file or an IO failure is logged with the exception and shown with `Dialogs` naming the demo; the panel
  stays visible because no file was opened. Errors opening the copy are reported by `Loader` itself; the view's error
  dialog covers copy failures.

The welcome panel is visible only while no file is open, so a demo copy can never be open when it is replaced.

Alternative: open the shipped file read-only. Rejected: saving marks or meta would fail or write into the install
folder, and repeated demos would show previous edits.

### Packaging follows templates/scripts

- `pom.xml`, `windows-build` profile: a `demo` resource in `stage-installer-input`, next to `scripts`.
- `pom.xml`, `macos-build` profile: a `copy-demo` execution next to `copy-scripts`.
- `assembly/linux/zip.xml`: a `<fileSet>` for `demo` like `scripts`.
- No change to workflow files; they build through the same POM.

## Risks / Trade-offs

- [Installer and repository grow by ~79 MB on every platform] → Accepted for offline demos; the files
  change rarely, so history grows once.
- [A future edit of a demo file bloats history again] → Replace demo files only deliberately; note it in `spec/overview.md`.
- [The demo copy needs up to ~79 MB in the user folder] → Only the last copy of each demo is kept.
- [The panel can be narrow when the chart pane is resized small] → The two columns stay in one row and their text
  wraps; at the minimum chart pane width the panel is cramped but usable.
