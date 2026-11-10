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

New `EmptyProfileView` (`@Component`, root package next to `SceneContent`) builds the panel in its constructor: a framed
drop zone holding the open hint, the two demo links and a row of three data groups; the documentation link row at the
bottom, outside the frame. `ProfileView.getCenter()` stacks
it over the charts scroll area below the toolbar (`StackPane` that grows to the remaining height), so the whole chart
area is the drop zone and the toolbar (geotagger button) stays usable. The previous scroll area children keep their
own VBox, so chart sizing is unchanged. The `openHint` label in `SceneContent` is removed.

Two equal growing spacers sit above and below the drop zone, so the drop zone stays centered and the links stay at
the bottom. There is no other block between the frame and the links. Spacings inside the frame and between the data
groups are set in the components stylesheet.

`EmptyProfileView` sets its own visibility on `FileOpenedEvent` / `FileClosedEvent` (`getFilesCount() == 0`, read inside
`Platform.runLater` so it does not depend on listener order). Collapsed charts still count as open files, which
matches the spec. Drag and drop events bubble to the `SceneContent` handlers unchanged.

Alternative: put the panel inside `Model.getChartsContainer()`. Rejected: the container is owned by `Model` and its
children are chart roots indexed by position.

### Drop highlight via drag events on the panel

`EmptyProfileView` observes `DRAG_ENTERED` / `DRAG_EXITED` / `DRAG_DROPPED` on itself (not consuming them) and toggles a
pseudo-class that highlights the dashed frame around the drop hint. Since the panel fills the chart area, this equals
"over the chart area". The frame encloses the hint, the demo links and the data groups, so the whole description of
what can be dropped reads as the drop zone; only the documentation links are outside. Drops are still accepted
anywhere on the window. Styling lives in the components stylesheet and uses color variables defined by every theme.

### No Open files button on the panel

The panel does not repeat the Open files toolbar button: the toolbar stays visible above the panel, and the panel is
about dropping files. `Saver` is not changed.

### Data groups are described, not generated from `FileTypes`

The drop zone shows three fixed groups in a centered `FlowPane`, each built by one private method from an icon path and two text
lines:

| Group | Icon | Lines |
|---|---|---|
| GPR | hyperbola under the ground line | GPR radargrams (SEG-Y), GSSI radargrams (DZT) |
| Sensor tables | anomaly peak on a baseline | Sensor tables (CSV, TXT, XYZ), GNSS positions (POS) |
| Navigation and sonar | sonar ping arcs | GPS tracks (NMEA), Sonar logs (SVLOG) |

The `FlowPane` has an unbounded preferred wrap length, so it asks for one row, and wraps whole groups when the chart
area is narrower; a `FlowPane` lays its children out at their preferred size, so group text is never cut. An `HBox`
was rejected: it shrinks the labels to an ellipsis on a narrow chart area.

The lines say what the data is and give the format in parentheses, so a new user recognizes their data and an
experienced one their format. A text list is maintained by hand: a new format needs a change to the panel. This is a
deliberate trade against the previous extension tags from `FileTypes.getSupportedExtensions()`, which listed every
extension but did not say what the files hold. Less common extensions (`.segy`, `.asc`, `.dat`, `.nme`, `.log`) are
not named. `FileTypes.getSupportedExtensions()` and the GPR probe in the view were added only for the tags and are
removed with them.

Alternatives: extension tags under each group (complete, but the group needs a rule for `.log` and `.txt`, which both
the NMEA and the CSV probes accept); a group title over short lines (one more line per group, says less).

### Panel icons are themed vector paths

The data icons (hyperbola, peak, ping arcs) and the upload icon above the drop hint are stroke-drawn shapes, so each is
an `SVGPath` with no fill and the stroke set by one style class in the components stylesheet using the dim text color,
the same as the hint and the group lines. The groups are drawn at reduced opacity, so the hint and its icon lead and the
groups stay secondary in both themes without a separate color per theme. Each path is
centered in a fixed 40×40 `StackPane`, so icons whose shapes have different heights still sit on the middle of their two
text lines. Paths are drawn at their final size instead of scaled, so the stroke width stays the same on all three. The
path constants live in `ResourceImageHolder` next to the toolbar icon paths. Alternative: PNG icons tinted with a fixed color, as `upload_file.png` was. Rejected:
they blur on HiDPI screens and need tint code per theme.

### Demo links

The demos are `Hyperlink`s in the drop zone under the hint, styled with the accent color so they stand out from the
dim hint text. Their text is the `DemoFile` title ("Gas Pipes GPR Demo", "Magnimbus Survey Demo"), which also names
the demo in the error dialog. Demo captions are removed. A link inside the frame is still clickable: drag events and
clicks are independent.

### Demo files: shipped folder, fresh user copy

- Repository folder `data/` holds the full demo files side by side, committed directly (no LFS, no CI download):
  - `data/demo-gpr.sgy` (Gas pipelines `2019-07-23-14-00-54-gpr.sgy`, 48 MB)
  - `data/demo-csv.csv` (MagNIMBUS single sensor `2023-07-06-12-38-33-quspin.csv`, 31 MB)
- `DemoFile` enum (`GPR`, `CSV`) holds the demo file name. `DemoFiles` (`@Component`) resolves `data/` with
  `Resources.resolvePath`, and for a demo: checks the source exists, deletes the whole `~/.geohammer/demo/` folder,
  copies the demo file into it and returns the copy. Deleting the folder removes the meta sidecars, the other demo's
  copy and any other leftovers. This is safe because the welcome panel, and so a demo, is only reachable while no file
  is open. The copy folder keeps the name `demo` so that a folder the app wipes is never mistaken for user data.
  Alternative: one copy subfolder per demo, wiping only that one. Rejected: two folders for two files, and keeping the
  other demo's copy has no value since its next run replaces it anyway.
- `DemoService.openDemo(demoFile)` (`@Service`) owns the opening flow and returns a `CompletableFuture`: it creates the
  copy on the shared virtual-thread executor, starts `Loader.load` on the FX thread (its task runner creates a
  `Stage`) and waits for loading in background. Copying stays in `DemoFiles` so it is unit-tested without `Loader`.
- `EmptyProfileView` only disables both demo links, calls `openDemo` and re-enables them on the FX thread when the future
  completes.
- A missing demo file or an IO failure is logged with the exception and shown with `Dialogs` naming the demo; the panel
  stays visible because no file was opened. Errors opening the copy are reported by `Loader` itself; the view's error
  dialog covers copy failures.

The welcome panel is visible only while no file is open, so a demo copy can never be open when it is replaced.

Alternative: open the shipped file read-only. Rejected: saving marks or meta would fail or write into the install
folder, and repeated demos would show previous edits.

### Packaging follows templates/scripts

- `pom.xml`, `windows-build` profile: a `data` resource in `stage-installer-input`, next to `scripts`.
- `pom.xml`, `macos-build` profile: a `copy-data` execution next to `copy-scripts`.
- `assembly/linux/zip.xml`: a `<fileSet>` for `data` like `scripts`.
- No change to workflow files; they build through the same POM.

## Risks / Trade-offs

- [Installer and repository grow by ~79 MB on every platform] → Accepted for offline demos; the files
  change rarely, so history grows once.
- [A future edit of a demo file bloats history again] → Replace demo files only deliberately; note it in `spec/overview.md`.
- [The demo copy needs up to ~79 MB in the user folder] → Only the last copy of each demo is kept.
- [The panel can be narrow when the chart pane is resized small] → The data groups wrap onto further rows; below the
  width of the widest group the panel is cut at the chart area edge.
- [A new supported format is not shown on the panel automatically] → Accepted for readable descriptions; adding a
  format to `FileTypes` includes updating the group lines.
