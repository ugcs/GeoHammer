# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

UgCS GeoHammer is a Java 21 desktop application for geophysical data processing and visualization. Built with Spring Boot 3.2 and JavaFX. Maven-based build (`pom.xml`). Package root: `com.ugcs.geohammer`.

### Build & Test Commands

```bash
mvn clean package                    # Full build (compiles with Error Prone + NullAway)
mvn test                             # Run all tests
mvn -pl . -Dtest=CsvParserTest test  # Run a single test class
mvn -pl . -Dtest=CsvParserTest#testParseLine test  # Run a single test method
mvn javafx:run                       # Run the application
mvn checkstyle:checkstyle            # Run checkstyle (warnings only, does not fail build)
```

Error Prone with NullAway runs during main compilation only (not test compilation). NullAway is configured in WARN mode with `OnlyNullMarked=true` and JSpecify annotations.

### Key Packages

- `chart/` — Chart rendering (GPR, sensor line charts, tool views)
- `format/` — File format parsers (CSV, DZT, SGY, SVLOG)
- `map/` — Map rendering layers (GPS track, grid, satellite, radar)
- `model/` — Domain model and events
- `service/` — Business logic services (gridding, palette, GPR processing)
- `view/` — Reusable JavaFX UI components
- `util/` — Utility classes
- `math/` — Mathematical algorithms (interpolation, filtering, smoothing)

---

## Architecture Overview

Entry point: `MainGeoHammer` (`MainGeoHammer.java`, extends `javafx.application.Application`).

### Bootstrap Sequence

`MainGeoHammer.init()` creates `AnnotationConfigApplicationContext("com.ugcs")` which scans 60+ Spring beans. The main Stage is built from `SceneContent` (`SceneContent.java`), which assembles a three-panel layout: `MapView` (`map/MapView.java`, left) | `ProfileView` (`ProfileView.java`, center) | `OptionPane` (`chart/tool/OptionPane.java`, right), with `StatusBar` (`StatusBar.java`) at the bottom. `AppContext` (`AppContext.java`) provides static access to the Spring context and primary Stage.

### Event-Driven Coordination

Components communicate via Spring's `ApplicationEventPublisher`. All events extend `BaseEvent` (`model/event/BaseEvent.java`). Publish via `model.publishEvent(new SomeEvent(...))`. Listen via `@EventListener` on `private void` methods.

#### Event Catalog

**File lifecycle:**
- `FileOpenedEvent` — file(s) successfully loaded. Published by `Loader`. Listened by `ProfileView`, `GpsTrack`, `SatelliteMap`, `TraceCutter`, etc.
- `FileSelectedEvent` — user selects a file to view. Published by `Model`/charts. Listened by 20+ components (tools, layers, views).
- `FileClosedEvent` — file removed. Published by `Chart.close()`. Listened by `Model`, `GpsTrack`, `GridLayer`, `UndoModel`, etc.
- `FileUpdatedEvent` — file data modified (e.g., after cropping). Published by `Model.cropTraces()`, `SensorLineChart`.
- `FileRenameEvent` — file renamed. Published by `Saver`. Listened by `GridLayer` (invalidates cached grids).
- `FileOpenErrorEvent` — file opening failed. Published by `Loader` on exceptions.

**Data/series:**
- `SeriesSelectedEvent` — data series selected in `SensorLineChart`. Listened by `GriddingTool`, `ScriptExecutionTool`.
- `SeriesUpdatedEvent` — series visibility/selection changed. Listened by `GriddingTool`.
- `GridUpdatedEvent` — gridding (spatial interpolation) completed. Published by `GridLayer`. Listened by `PaletteView`.
- `DepthRangeUpdatedEvent` — GPR depth slider moved. Listened by `Model` (syncs depth across all open GPR files).
- `TemplateUnitChangedEvent` — data units changed (e.g., ns to m). Listened by `Model` (updates depth sliders).

**Task lifecycle:**
- `TaskRegisteredEvent` / `TaskCompletedEvent` — async task started/finished. Published by `TaskService`. Listened by `TaskStatusView`.

**State:**
- `UndoStackChanged` — undo stack modified. Published by `UndoModel`. Used to enable/disable undo button.
- `WhatChanged` — generic UI/rendering state change with `Change` enum: `traceValues`, `traceCut`, `windowresized`, `justdraw`, `mapscroll`, `profilescroll`, `mapzoom`, `adjusting`, `updateButtons`, `fileSelected`, `csvDataZoom`, `traceSelected`. Listened by 15+ components for rendering updates.

#### Key Event Workflows

**File open:** `Loader.load()` → `FileOpenedEvent` → `ProfileView`, `GpsTrack`, `SatelliteMap` update → `WhatChanged(updateButtons, justdraw)` triggers rendering.

**File select:** Chart/Model → `FileSelectedEvent` → 20+ components load state for selected file (tools, layers, palette, inspector).

**Data modification (crop/filter):** `Model.cropTraces()` → `FileUpdatedEvent` → `WhatChanged(traceCut)` → `GridLayer` invalidates grid, `GpsTrack`/`QualityLayer` refresh, charts redraw.

**Gridding:** `GridLayer.performGridding()` async → `GridUpdatedEvent` → `PaletteView` refreshes.

**Depth sync:** Depth slider moved → `DepthRangeUpdatedEvent` → `Model` syncs across all open GPR files.

**Task tracking:** `TaskService.registerTask()` → `TaskRegisteredEvent` → `TaskStatusView` shows progress → task completes → `TaskCompletedEvent` → status bar clears.

### Central State: Model

`Model` (`model/Model.java`) holds all application state: open files (`Map<SgyFile, Chart>`), the active file, `MapField` (`model/MapField.java`, geographic viewport), auxiliary elements (flags, rulers, found places), and the charts UI container. Most components receive `Model` via constructor injection.

### Data Pipeline

**File formats** (`format/`): Parsers produce `SgyFile` subclasses or populate `GeoData` with `ColumnSchema`-defined columns. CSV parsing uses YAML templates (`templates/*.yaml`) configured via `TemplateSettings`. Supported formats: CSV, DZT (GSSI GPR), SGY, BIN, BLOCK, SVLOG, KML, NMEA.

**Charts** (`chart/`): `GPRChart` (`chart/gpr/GPRChart.java`) and `SensorLineChart` (`chart/csv/SensorLineChart.java`) extend abstract `Chart` (`chart/Chart.java`, which extends `ScrollableData` (`chart/ScrollableData.java`)). Charts manage visualization, zoom, trace selection, and flag placement.

**Tools** (`chart/tool/`): `ToolView` (`chart/tool/ToolView.java`) subclasses (GriddingTool, PaletteView, FilterToolView, etc.) provide data processing UI. Lifecycle: `selectFile()` -> `loadPreferences()` -> `updateView()`.

**Map layers** (`map/layer/`): `GridLayer`, `GpsTrack`, `SatelliteMap`, `RadarMap`, `QualityLayer` implement a layer interface with `draw(Graphics2D, MapField)`. Rendered to `BufferedImage` via AWT, converted to JavaFX `Image`.

**Services** (`service/`): Business logic — `GriddingService` (`service/gridding/GriddingService.java`, spatial interpolation), `Palette` (`service/palette/Palette.java`, color mapping), `TraceTransform` (data transforms), signal processing filters. `TaskService` (`service/TaskService.java`) tracks background tasks via `CompletableFuture`.

**Styles** (`resources/styles/`): CSS stylesheets for JavaFX UI. `Styles` (`view/Styles.java`) is the entry point for style management — provides `addResource(Parent, String)` to load CSS from resources and `urlFromResource(String)` for URL resolution. Theme styles in `resources/styles/theme/`, component styles in `resources/styles/`.

### Key Utilities

- `Check` (`util/Check.java`) — precondition assertions (`notNull`, `notEmpty`, `condition`, `range`)
- `Nulls` (`util/Nulls.java`) — null-safe helpers (`toEmpty(list)` for safe iteration)
- `Views` (`view/Views.java`) — JavaFX UI factory methods (buttons, spacers, colors)
- `FileTypes` (`util/FileTypes.java`) — file type detection from extension/content
- `SphericalMercator` (`math/SphericalMercator.java`) / `CoordinatesMath` (`math/CoordinatesMath.java`) — geographic coordinate transforms
- `SinglePendingExecutor` (`util/SinglePendingExecutor.java`) — "last-wins" task submission
- `PaintLimiter` (`view/PaintLimiter.java`) — frame-rate throttling via `AnimationTimer`

---

## Coding Principles

### General Philosophy

- **Minimal changes** — Only modify what is necessary for the task. Do not refactor surrounding code unless it directly serves the goal.
- **No over-engineering** — Avoid abstractions for one-time operations. Three similar lines are better than a premature helper.
- **Delete dead code entirely** — Never leave `@Deprecated` annotations, commented-out code, or TODO markers as substitutes for actual implementation. Remove unused code completely.
- **Conservative dependencies** — Prefer platform APIs (Java stdlib, Spring, JavaFX) over new third-party libraries.
- **Fix root causes** — Fix the underlying problem, not the symptom. Add defensive guards against related edge cases. Make invalid states unrepresentable.
- **Reduce allocations in hot paths** — Reuse buffers, batch operations (`Arrays.fill`), avoid throwaway objects. Move expensive computations to background threads, update UI via `Platform.runLater()`.

### Code Style

- **~120 character line length** — No strict limit, but keep lines readable.
- **Single blank line** between methods. Blank line after field declarations before constructor.
- **No wildcard imports** — Every import is explicit.
- **No Javadoc** on new code. Use brief inline comments only where the logic is non-obvious (algorithm notes, section separators, invariants).

### Naming

- Prefer descriptive and short names for classes, methods and variables. Naming forces code readability so pick names carefully and consistently.
- **Classes**: Descriptive nouns. Suffixes by role: `*View`, `*Tool`, `*Service`, `*Schema`, `*State`, `*Filter`.
- **Methods**: Verb-first. `create*` for factories, `on*` for event handlers, `get*`/`is*` for accessors.
- **Variables**: Descriptive. Short names (`i`, `j`, `w`, `h`) only for loop counters and local math.

### `final` Keyword

- All injected dependencies: `private final`.
- Container fields (maps, lists) that are initialized once: `final`.
- **Do NOT** mark local variables or method parameters as `final`.

### Null Handling

- Use `@Nullable` and `@NonNull` from `org.jspecify.annotations` where they add clarity, but usage is currently inconsistent across the codebase. Do not overuse annotations in new code — add them when they prevent real bugs, not as blanket decoration.
- `@NullMarked` package annotation is used occasionally but not consistently. Do not add it to new packages unless specifically requested.
- Prefer returning `@Nullable` over sentinel values (e.g., return `@Nullable Range` not `Range(0, 0)`).
- Guard clauses with early return: `if (file == null) return;`.
- `Check.notNull()` for preconditions at method entry points and record constructors.
- `Objects.equals()` for null-safe comparison.
- `Nulls.toEmpty(list)` for null-safe iteration.

### Error Handling

- Checked `IOException` propagated on file operations — do not catch prematurely.
- `IllegalStateException` for unrecoverable logic errors.
- `UncheckedIOException` wrapping `IOException` in utility methods.
- Log exceptions with `log.error("Error", e)` — always include the exception object.
- Replace `e.printStackTrace()` and `System.err.println()` with SLF4J logging.
- Graceful degradation for data issues: log and skip bad records, do not throw.

### Logging

- SLF4J via `LoggerFactory.getLogger(ClassName.class)`.
- `log.error` — unexpected exceptions.
- `log.warn` — recoverable data issues (e.g., invalid checksums).
- `log.info` — lifecycle events.
- `log.debug` — detailed diagnostics, almost never.

---

## Architectural Patterns

### Spring Framework

- **Constructor injection only** — never `@Autowired` on fields.
- `@Component` / `@Service` for Spring-managed beans.
- `@EventListener` on `private void` methods for event handling.
- `model.publishEvent(new SomeEvent(...))` for event dispatch.
- `@PreDestroy` and `DisposableBean` for cleanup lifecycle.
- `@Primary` on executor beans, `destroyMethod = "close"` for executor lifecycle.

### Class Structure Order

1. Static constants (grouped by purpose with comment labels)
2. Instance fields (injected dependencies first, then mutable state)
3. Constructor
4. Public/API methods
5. Private helper methods
6. `@EventListener` methods
7. Inner types (records, enums, static inner classes)

### JavaFX UI

- **Programmatic UI construction** — no FXML. Build layouts in constructors.
- **Lazy window creation**: `@Nullable private Stage window;` initialized on first `show()`.
- **Window factory pattern**: `private Stage createWindow()` method with `initOwner(AppContext.stage)`, `initStyle(StageStyle.UTILITY)`, `setOnCloseRequest(e -> { e.consume(); hide(); })`.
- **Thread-safe UI updates**: Always use `Platform.runLater()` from non-FX threads.
- **Reusable UI helpers**: Use `Views.createSpacer()`, `Views.createGlyphButton()`, `Views.createSvgButton()`.
- **Inline CSS for styling**: `node.setStyle("-fx-border-color: ...");` for simple cases.
- **Event wiring in constructor**: `button.setOnAction(e -> ...)`, `property.addListener(this::onValueChanged)`.

### Concurrency

- **`AtomicReference` / `AtomicBoolean`** for lock-free state.
- **`synchronized`** blocks kept minimal — prefer atomics when possible.
- **`SinglePendingExecutor`** pattern for "last-wins" task submission.
- **`PaintLimiter`** for frame-rate throttling via `AnimationTimer`.
- **`CompletableFuture`** for async service results with lazy caching via `AtomicReference`.
- **`ConcurrentHashMap`** for thread-safe maps.
- Virtual threads for async executor (`Executors.newVirtualThreadPerTaskExecutor()`).
- `Platform.runLater()` for UI updates from background threads.

### Modern Java (21+)

- Records for value types.
- Switch expressions: `return switch (type) { case A -> ...; };`.
- Pattern matching for `instanceof`: `if (obj instanceof SomeType t)`.
- `Math.clamp()` for range clamping.
- `List.of()`, `List.getFirst()` for modern collections.
- No `var` usage.
- **No stream API** — use traditional `for` loops for data iteration.
- Lambdas for callbacks, method references where applicable.

---

## Testing

- **JUnit 5** (`org.junit.jupiter.api`).
- Test public APIs directly — no reflection (`setAccessible`) in tests.
- No mocking framework — use real objects.
- `assertEquals`, `assertNotEquals` from JUnit 5 assertions.

---

## Commit Messages

- **Imperative mood**: "Implement...", "Fix...", "Add...", "Remove...", "Support...", "Refactor...".
- **Precise verbs**: "Implement" (new feature), "Add" (addition to existing), "Fix" (bug fix), "Remove" (deletion), "Support" (new format/data type), "Refine" (improvement), "Limit" (performance constraint).
- **Issue reference** at the end: `(#123)`.
- **40-70 characters** typical length, up to ~120 when needed for clarity.
- **No commit body** for simple changes. Bullet list body for multi-part commits.

