# Coding Conventions

Older code does not follow all of these (field injection, `var`, streams, handlers not named `on*`). Apply them to new
and modified code; do not rewrite untouched code just to comply.

## Style

- ~120 char lines, no wildcard imports, blank line between methods and after the field block.
- No Javadoc. Inline comments only for non-obvious logic (algorithms, invariants, section separators).
- No magic numbers: name them as `static final` constants.
- `final` on injected dependencies and once-initialized containers; never on locals or parameters.
- Java 21 idioms: records for values, switch expressions, `instanceof` patterns, `Math.clamp`, `List.of`/`getFirst`.
  No `var`. Prefer `for` loops over streams for data iteration.

## Naming

- Short and descriptive. Classes are nouns with role suffixes (`*View`, `*Tool`, `*Service`, `*Schema`, `*State`,
  `*Filter`); methods are verb-first (`create*` factories, `on*` event handlers, `get*`/`is*` accessors). Short
  variable names only for loop counters and local math.
- A name states exactly what the code does; rename when behavior changes.
- One name per concept across classes; never one name for two concepts. Prefer established domain terms.
- No generic suffixes (`Data`, `Info`, `Manager`, `Helper`). Domain classes are named after the concept,
  serialization/transport classes after their role.

## Class Layout

Static constants → fields (injected first, then state) → constructor → public methods → private helpers →
`@EventListener` methods → inner types. Group related members; accessors follow field order.

## Design

- Reuse existing utilities and follow the patterns of the area being extended.
- No over-engineering: no abstractions for one-time use; three similar lines beat a premature helper.
- Delete dead code entirely: no commented-out code, `@Deprecated` leftovers or TODO stand-ins.
- Prefer platform APIs (JDK, Spring, JavaFX) over new dependencies.
- Make invalid states unrepresentable.
- No hidden side effects: a method that builds a result does not mutate other state.
- Do not add methods to a shared interface that only one implementation needs.
- Views render and delegate; controllers hold no state.
- Enums instead of int/byte codes; do not rely on `ordinal()` for persisted or mapped values.
- Persisted formats round-trip: the model keeps every persisted field even if the UI hides it. Dropping a field is an
  explicit decision.
- Numeric code prefers primitives and primitive arrays over boxed types. Hot paths reuse buffers and avoid throwaway
  allocations. Do not trade memory for marginal speed.

## Nulls and Errors

- JSpecify `@Nullable` only where it prevents real bugs; do not add `@NullMarked` unless asked.
- Early-return guard clauses. `Check.notNull()`/`Check.condition()` at entry points and in record compact constructors.
  `Objects.equals()` for null-safe comparison, `Nulls.toEmpty()` for null-safe iteration.
- Propagate `IOException` from file operations; wrap in `UncheckedIOException` in utilities. `IllegalStateException`
  for logic errors.
- Bad data records: log and skip, do not throw.
- Error messages state only what the code knows: no assumptions about other components, no internal details.
- SLF4J only (never `printStackTrace`/`System.err`); always pass the exception to `log.error`. `warn` for recoverable
  data issues, `info` for lifecycle, `debug` rarely.

## Spring

- Constructor injection only. `@Component`/`@Service` beans, including window-scoped views, models and controllers —
  inject them rather than `new`.
- `@EventListener` on `private void` methods; publish via `model.publishEvent(...)`. `@PreDestroy` for cleanup.

## JavaFX

- Programmatic UI, no FXML; build layouts and wire events in constructors.
- The FX thread is for UI only: parsing, IO, data mutation and computation run in background; marshal only the UI
  update with `Platform.runLater()`.
- Secondary windows extend `UtilityWindow`; every new `Scene` is registered with `ThemeService`.
- Style classes, not inline styles; never both on one node. Custom classes go to the components stylesheet; changes to
  standard control classes are applied in every theme.

## Concurrency

Prefer atomics over `synchronized`; keep locked sections minimal. Use `SinglePendingExecutor` for last-wins tasks,
`PaintLimiter` for repaint throttling, the shared virtual-thread executor for async work.

## Tests

JUnit 5 with real objects: no mocking frameworks, no reflection; test public APIs.

## Commit Messages

Imperative, 40–70 chars (up to ~120), issue reference at the end: `Fix grid jitter on map zoom (#123)`. Precise verbs:
Implement (feature), Add (to existing), Fix, Remove, Support (format/data type), Refine, Limit (performance). No body
for simple changes; bullet list body for multi-part commits.
