# Overview

UgCS GeoHammer is a desktop app for processing and visualizing geophysical survey data: GPR radargrams and sensor
time series (magnetometers, sonar, NMEA, any CSV). Java 21, JavaFX 21, plain Spring context (Spring Boot is only the
parent POM — no auto-configuration), Maven. Package root: `com.ugcs.geohammer`.

```bash
mvn clean package                    # build; Error Prone + NullAway (WARN) on main sources only
mvn test                             # all tests
mvn -Dtest=CsvParserTest#validCsv test        # single test class / method
mvn javafx:run                       # run the app
mvn checkstyle:checkstyle            # warnings only, never fails the build
```

Runtime folders (resolved relative to the working dir, then to the jar): `templates/` (CSV templates, watched for
changes), `scripts/` (Python scripts). User settings: `~/.geohammer/templates-settings.properties`.

# Domain Glossary

- **GPR file** — `TraceFile` (SEG-Y `GprFile`, GSSI `DztFile`). A profile of **traces**; each trace is a vertical array
  of **samples** (depth/time axis). Shown in `GPRChart`.
- **Data file** — sensor data as rows: `CsvFile`, `SonarFile` (svlog), `NmeaFile`. Shown in `SensorLineChart`.
- **Trace** — one position along a profile: a GPR `Trace` or one data row. Addressed by index; `TraceKey(file, index)`.
- **Column / series** — a named value per row (`Column` in `ColumnSchema`). A *series* is a column plotted in the
  chart; the terms are interchangeable. Columns have a unit, visibility (`display`) and `readOnly` (blocks user edits
  only; programmatic edits are allowed).
- **Semantic** — role of a column: Line, Mark, Latitude, Longitude, Altitude, Altitude AGL, TMI.
- **Line** — survey line: consecutive rows with the same value in the Line column. Line ranges are derived and cached
  per file. Lines are split, merged, cropped and removed via `TraceTransform`.
- **Mark / flag** — a marked trace. In memory a `FoundPlace` in the file's aux elements; persisted as the Mark column
  (CSV) or in meta (GPR).
- **Meta** — editable overlay over a source file that is never rewritten on save: line structure, marks, sample range,
  contrast, color scale. Stored as a JSON sidecar `<file name with ext>.geohammer` (`MetaDocument`). Used by GPR, sonar
  and NMEA files (`SgyFileWithMeta`); CSV files are rewritten instead.
- **Template** — YAML description of a CSV format (separators, header, column mapping, units, match regex). The
  template name also keys per-format user settings.
- **Grid** — spatial interpolation of a series onto a lat/lon raster. Params: cell size and blanking distance (max
  distance from data where values are shown). Render filters: range, analytic signal, hill shading, smoothing.
- **Palette** — value-to-color mapping (`PaletteType`: linear, gaussian, histogram) over a color ramp (`SpectrumType`).

# Data Model

- `SgyFile` is the root of every open file: `open`, `save`, `copy`, `createSnapshot`, `getGeoData`, aux elements,
  version, lock. `CsvFile` extends it directly; `SgyFileWithMeta` → `TraceFile` / `SonarFile` / `NmeaFile`.
- `GeoData` is one row: schema + `Object[]` values + timestamp. Meta-based files expose rows as `TraceGeoData` (a row
  pointing at a raw trace/packet index). GPR cropping edits meta rows only; raw traces stay intact.
- **Unsaved/version**: `setUnsaved(true)` assigns a new globally unique version; `isUnsaved()` compares it to the saved
  one. Any data mutation must call `setUnsaved(true)`.
- **Caches**: `tracesChanged()` invalidates derived per-file caches (line ranges, distances, sample statistics). Call it
  after changing rows/traces (`Model.reload` does it).
- **Undo**: `UndoModel` holds a bounded stack of `UndoFrame`s, each a list of per-file `FileSnapshot`s (row data or
  meta + optionally raw traces, spilled to temp files). Restoring a snapshot restores data, aux elements and version.
  Take the snapshot before mutating. Series filters are intentionally not undoable (they add new columns).
- **Model** (`Model`) is the central state: open files and their charts, current file, map viewport (`MapField`),
  selected traces, flags. Most components get it injected.
- **Locking** (`SgyFile.withLock`) and optimistic version checks are used only by the MCP layer; UI paths do not lock.

# Runtime

- **Startup**: `MainGeoHammer.init()` (launcher thread) creates `AnnotationConfigApplicationContext("com.ugcs")`; all
  beans including the main UI tree (`SceneContent`: map | profile | tool panel + status bar) are built here, off the
  FX thread. `start()` (FX thread) creates the scene, shows the stage, then starts the MCP server and opens files from
  arguments. Start anything that may touch AWT only after the stage is shown — AWT init during stage build deadlocks on
  macOS.
- **Shutdown**: closing the window calls `context.close()`; cleanup goes through `@PreDestroy`. Settings are written to
  disk only then (or on explicit `save()`).
- **Static access**: `AppContext` exposes the Spring context, primary stage and model for non-bean code.
- **Threads**:
  - FX thread — scene graph changes, chart reload, undo stack changes, publishing UI events.
  - Shared virtual-thread `ExecutorService` bean — file loading, filters, gridding, scripts. Register long tasks in
    `TaskService` to show progress in the status bar.
  - `SinglePendingExecutor` — last-wins recomputation (grids, projections).
  - Map layers render to AWT `BufferedImage` on their own render queue thread; the map composes frames and repaints on
    the FX pulse via `PaintLimiter`.
  - MCP requests run on virtual threads and hop to the FX thread only for UI work.

# Events

Spring events extending `BaseEvent`, published via `model.publishEvent(...)`. Delivery is synchronous on the
publishing thread; listeners that touch UI must tolerate non-FX publishers. Find listeners by searching usages.

- `FileOpenedEvent` — files finished loading (FX thread). `FileOpenErrorEvent` — a file failed to open.
- `FileSelectedEvent` — active file changed (null when cleared). Tools and layers reload their state for it.
- `FileClosedEvent` — file closed; drop all per-file state (caches, undo frames, grids).
- `FileUpdatedEvent` — file columns or data changed (published by `Model.reload`).
- `FileRenameEvent` — file saved under a new name; re-key per-file state.
- `SeriesSelectedEvent` / `SeriesUpdatedEvent` — series selected / visibility or selection changed.
- `GridUpdatedEvent` — grid computed or cleared for a file.
- `DepthRangeUpdatedEvent` — GPR depth window changed; synced to other GPR charts.
- `TemplateUnitChangedEvent` — x-axis unit changed; synced to charts with the same template.
- `TaskRegisteredEvent` / `TaskCompletedEvent` — background task lifecycle (`TaskService` only).
- `UndoStackChanged`, `ThemeSelectedEvent` — undo stack / theme changed.
- `WhatChanged(Change)` — generic invalidation. Key values: `justdraw` (repaint), `traceCut` (trace set changed),
  `traceValues` (sample values changed), `traceSelected`, `mapscroll`/`mapzoom`, `csvDataZoom`, `updateButtons`.

# Recipes

**Change file data and refresh**
1. Off the FX thread: snapshot (`file.createSnapshot()`, or `createSnapshotWithTraces()` when GPR samples change).
2. Mutate rows/traces/columns, then `file.setUnsaved(true)`.
3. On the FX thread: push the snapshot as an `UndoFrame`, then `model.reload(file)` (invalidates caches, reloads the
   chart, refreshes aux elements, publishes `traceCut`, `justdraw`, `FileUpdatedEvent`).

Line structure changes go through `TraceTransform`, which already does all of the above.

**Add a tool to the right panel**
1. Extend `ToolView` (or `FilterToolView` for apply-style filters running on the executor) as a `@Component`.
2. Implement `isVisibleFor(file)`, `updateView()`, `loadPreferences()`/`savePreferences()` (per-template keys in
   `Settings`), and an `@EventListener` on `FileSelectedEvent` calling `selectFile(...)` on the FX thread.
3. Register it in `OptionPane` (constructor parameter + toggle box; list order is display order).

**Support a file format**
1. Implement an `SgyFile` subclass (`SgyFileWithMeta` if the source should stay untouched and edits go to meta).
2. Add detection to `FileTypes` (extension and/or content probe) and a branch in `Loader.openFile`; order matters —
   text formats fall back to CSV.
3. Handle save paths in `Saver` and the chart type in `Model`; add the template name in `Templates`.

**CSV templates**
Matching (`FileTemplates.findTemplate`) reads the first lines of a file and tries `*.yaml` templates from the file's
own directory first, then the app `templates/` folder; the first `match-regex` hit wins. No match opens the template
editor when interactive, otherwise fails. User templates are never saved into the app folder.

**Add an MCP tool**
Extend `McpTool` (`getName`, `buildSchema`, `invoke`; override `modifiesFiles()` for writes), register it in the
`McpTools` constructor. Describe tools for agents without source access; reference other tools as `{{tool_name}}`.
Locking, version checks and undo tracking are handled by `McpTools.callTool`.

# Packages

- `chart` — GPR and sensor charts; `chart/tool` — right-panel tools
- `format` — file types, readers and writers
- `map` — map view and layers (track, grid, satellite, radar, quality)
- `model` — `Model`, events, undo, templates, column schema
- `service` — gridding, palettes, transforms, GPR processing, quality checks, Python scripts, tasks
- `math` — interpolation, filters, statistics, projections
- `mcp` — embedded MCP server (HTTP JSON-RPC on 127.0.0.1) and tools
- `template` — CSV template editor
- `settings`, `geotagger`, `feedback`, `release`, `analytics` — settings window and auxiliary features
- `view` — reusable UI controls, windows, themes; `util`, `io` — general helpers

# Reusable Utilities

- `Check` — preconditions; `Nulls`, `Strings` — null-safe helpers; `Result` — success/error value
- `Numbers` — fast number parsing; `Text` — text and multi-format date parsing; `Regex` — regex helpers
- `QuickSelect` — k-th element / median without sorting; `PrefixSum`, `SegmentTree`, `KdTree`
- `FileTypes`, `FileNames`, `Templates` — file type detection, names, template name of a file
- `SphericalMercator`, `CoordinatesMath`, `UtmProjector` — coordinate math
- `SinglePendingExecutor`, `ReentranceGuard` — last-wins tasks, re-entrance guard
- `Views`, `Dialogs`, `Listeners`, `Bindings`, `UtilityWindow`, `PaintLimiter` — UI construction and wiring
