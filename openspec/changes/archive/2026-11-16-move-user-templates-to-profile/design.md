# Design

## Context

`FileTemplates` (Spring bean, `InitializingBean`) had one templates folder,
`Resources.resolvePath("templates")`: the working directory first, then the folder next to the jar.

- `afterPropertiesSet` loads `*.yaml` from that folder on the launcher thread while the Spring context is built.
- `watchTemplates` (`@Async`, started from `Config`) watches that folder on a virtual thread and reloads the list on
  any change with `templates.clear()` then `addAll(reloaded)`.
- `findTemplate(file)` matches against the file's own folder first (read on every call), then the global list.
- `getTemplate(name)` returns the first template in the global list with that name. Per-template settings and
  `SeriesSelectorView` look templates up by name.
- Settings tabs (`SettingsTab`: `load`, `validate`, `save`) run on the FX thread. A setting is stored by the bean
  that owns it through `Settings` (`PythonInterpreter.setPath`, `Model.setTraceLookupThreshold`).
- `~/.geohammer` is built inline in `Settings`, `DemoFiles` and `UserPropertiesService` for their own files.

The change touches no file data. Snapshots, undo, `setUnsaved` and cache invalidation are not involved.

## Goals / Non-Goals

**Goals:**
- One source for the user folder: its default, its setting and its subfolders are defined in one class, used by
  templates now and by scripts in stage 2 of #1078.
- The folder applies without a restart.
- The existing loader, list type and watcher loop of `FileTemplates` stay as they are.

**Non-Goals:**
- Routing the settings file, demo copies and analytics properties through the user folder. They keep their inline
  `~/.geohammer` paths: the settings file cannot live in a folder whose path it stores.
- The template editor and the handling of files users put into the application folder.

## Decisions

### `UserFolder` is the single owner

New bean `com.ugcs.geohammer.UserFolder`, next to `Settings`:

- Spring constructor `UserFolder(Settings, ApplicationEventPublisher)` (annotated `@Autowired`) reads the folder from
  `Settings` (group `userFolder`, key `path`) and falls back to `~/.geohammer`. A stored value that is not a valid
  path is logged and replaced by the default, so it cannot stop startup. A second public constructor takes the path
  for tests.
- `getPath()` and `getTemplatesPath()` (`<user folder>/templates`). The scripts change adds `getScriptsPath()`.
- `setPath(@Nullable Path)`: `null` selects the default; a call with the current folder does nothing; otherwise it
  stores the setting (removes the key for the default) and publishes `UserFolderChangedEvent`. The field is
  `volatile`: it is written on the executor and read by loader and watcher threads. The comparison, the field and
  the stored setting change under one lock, so concurrent calls cannot leave them inconsistent; the event is
  published outside the lock, because listeners read files.
- The `templates` subfolder name is a constant of `UserFolder`, so it does not depend on `FileTemplates`.
- `UserFolder` does no file IO. Each consumer creates its own subfolder.

It publishes through `ApplicationEventPublisher` and not `model.publishEvent`: `Model` depends on `FileManager` and
so on `FileTemplates`, which depends on `UserFolder`, so using `Model` here would close a dependency cycle. `Loader`
publishes the same way.

*Alternative:* two independent settings, one per kind of files. Rejected: two places to change and back up, and the
same read, validate and store logic twice.

*Alternative:* `FileTemplates` owns the folder (the previous version of this change). Rejected for the same reason
once scripts need it too.

### `FileTemplates` reads the folder from `UserFolder`

- Spring constructor `FileTemplates(Status, UserFolder)` (annotated `@Autowired`); a second public constructor adds
  the application folder path for tests. The application folder keeps its names (`templatesPath`,
  `getTemplatesPath()`).
- The existing resolver-based `loadTemplates()` body becomes `loadTemplates(Path)`, and `loadTemplates()` merges:

```
merged = user templates (userFolder.getTemplatesPath())
       + app templates whose name is not used by any user template
```

- `afterPropertiesSet` creates the user templates folder (on `IOException` it logs a warning and goes on) and loads.
  `findTemplate` and `getTemplate` keep reading the global list, so the override applies to matching and to lookup by
  name without further changes.

*Alternative:* keep two lists and search them in turn. Rejected: `getTemplate(name)` and `getTemplates()` would each
need the override rule.

### Watcher follows the user templates folder

- `watchTemplates` registers the application folder as before, keeps its `WatchService` in a `volatile` field and
  registers the user templates folder through `watchUserTemplates()`, which cancels the previous key and registers
  the current folder when it is a directory.
- The reload code of the watcher loop moves into `reloadTemplates()`.
- Package-private `@EventListener onUserFolderChanged(UserFolderChangedEvent)` creates the new user templates folder, calls
  `watchUserTemplates()` and `reloadTemplates()`. The event is delivered on the publishing thread, the executor.
- `watchUserTemplates()` and `reloadTemplates()` are `synchronized`: the watcher thread and a folder change can run
  at the same time, and the loader shares one `Yaml` instance, which is not thread-safe.

### Settings field

`GeneralTab` gets a "User folder" row: a text field and a "Browse..." button with a `DirectoryChooser`. The label
names the `templates` subfolder, so users do not put yaml files into the folder root.
- `load` shows `userFolder.getPath()`.
- `validate` rejects an invalid path and a path that is not an existing directory. An empty field and the current
  folder always pass. The path is made absolute and normalized first.
- `save` passes the path (`null` for an empty field) to `userFolder.setPath` on the shared `ExecutorService`,
  because listeners of the change read files. A failure there is logged.

### Startup warning

`MainGeoHammer` keeps checking `fileTemplates.getTemplates().isEmpty()`. It now checks the merged list, which meets
the spec without code changes there.

### Left as is on purpose

- The watcher still does `clear()` then `addAll()`, so a file opened between the two calls finds no templates. This
  existed before the change.
- Built-in templates keep the resolver's order.
- The template editor: "Save as" opens next to the data file.

## Risks / Trade-offs

- [Templates users keep in the application folder] They load as before and can be lost on reinstall. → Out of scope
  here (separate task). Release note and wiki: put custom templates in the user folder.
- [Yaml files put into the user folder root] They are not loaded: templates are read from the `templates`
  subfolder. → The field label says so.
- [Templates and scripts cannot be in unrelated folders] → Accepted. A separate setting can be added on top later.
- [Two user templates with the same name] Both stay in the list and the first one loaded wins, as with duplicates
  today. → No change.
- [The user templates folder cannot be created, for example a read-only location] → Logged. The app keeps working
  with built-in templates. The folder is not watched until the next start or folder change.
- [Files left in the previous user folder after a change] They are no longer used. → Not moved on purpose.
- [The folder-change listener is not private] `FileTemplates` is proxied for `@Async`, and Spring cannot invoke a
  private `@EventListener` on a proxy, so `onUserFolderChanged` is package-private. → `FileTemplatesTest` calls it
  directly; the watcher switch is checked in the running app (task 2.2).

## Migration Plan

No data migration. Rollback is a plain revert: the settings key and the user folder are ignored by older versions.
