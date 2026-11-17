# Design

## Context

Stage 1 of #1078 (commit `5012d57a`, only on the #1078 branch) added:

- `UserFolder` (bean): the `userFolder.path` setting, `~/.geohammer` by default, `getTemplatesPath()` =
  `<user folder>/templates`, `setPath` storing the setting under a lock and publishing `UserFolderChangedEvent`.
- `FileTemplates`: reads the user templates folder from `UserFolder`, creates it in `afterPropertiesSet`, merges
  user and application templates, watches both folders. A package-private `@EventListener onUserFolderChanged`
  creates the new folder, re-registers the watcher (`watchUserTemplates`) and reloads (`reloadTemplates`); it cannot
  be private because the bean is proxied for `@Async watchTemplates`.
- `GeneralTab`: the "User folder" field with "Browse...", validation and `setPath` on the shared executor.

Other settings are owned by the bean that uses them: `PythonInterpreter.setPath` on the "Scripts" tab,
`Model.setTraceLookupThreshold` on the "General" tab. `Settings` has a no-argument constructor and no dependencies.

The change touches no file data: snapshots, undo, `setUnsaved` and cache invalidation are not involved.

## Goals / Non-Goals

**Goals:**
- The templates folder is one setting with its own default, stored by one small class.
- Loading, merging, matching and the watcher loop of `FileTemplates` stay as they are.
- No event or listener for the folder change.

**Non-Goals:**
- A shared helper for folder settings. The scripts change adds its own folder setting next to the code that uses it.
- Splitting `FileTemplates` into loader, matcher and watcher classes. It is a wide refactor; propose it separately.

## Decisions

### `UserTemplatesFolder` stores the setting, `FileTemplates` applies it

New bean `com.ugcs.geohammer.model.template.UserTemplatesFolder`, only the setting:

- Constructor `UserTemplatesFolder(Settings)` reads group `userTemplates`, key `path` into a `volatile Path`. An empty
  value means the default, `~/.geohammer/templates`. A value that is not a valid path is logged as a warning and
  replaced by the default, so it cannot stop startup.
- `getPath()` returns the current folder.
- `synchronized setPath(@Nullable Path)`: `null` selects the default; the current folder does nothing; otherwise it
  stores the setting (removes the key for the default) and switches the field together.
- No file IO and no events.

`FileTemplates` uses it:

- Constructors: Spring `FileTemplates(Status, UserTemplatesFolder)` (annotated `@Autowired`) delegating to a public
  `FileTemplates(Status, UserTemplatesFolder, Path templatesPath)` for tests. `templatesPath` keeps meaning the
  application folder.
- `createUserTemplatesFolder`, `watchUserTemplates` and `loadTemplates()` read `userTemplatesFolder.getPath()`
  instead of `userFolder.getTemplatesPath()`.
- `getUserTemplatesPath()` returns `userTemplatesFolder.getPath()`.
- `synchronized setUserTemplatesPath(@Nullable Path)`: remembers the current folder, calls
  `userTemplatesFolder.setPath(path)`, and returns when the folder did not change; otherwise it creates the folder
  (a failure is logged), calls `watchUserTemplates()` and `reloadTemplates()`. These two are already `synchronized`
  on the bean, so a folder change and a watcher reload cannot interleave.
- `onUserFolderChanged` and the comment about the proxy are removed.

The settings window talks only to `FileTemplates`, so no event is needed: the bean that reloads templates is the one
that is told about the change.

*Alternative:* `FileTemplates` stores the setting itself, like `PythonInterpreter.setPath`. Rejected: storing a
setting (key, default, invalid value) is a reason to change unrelated to loading templates, and `FileTemplates`
already loads, matches and watches them.

*Alternative:* rename `UserFolder` to `TemplatesFolder` (default `~/.geohammer/templates`, no subfolder) and keep the
event. Rejected: keeps an event and a listener that only `FileTemplates` uses, including the package-private
listener workaround.

*Alternative:* keep `UserFolder` and add a templates folder setting next to it. Rejected: two settings for one folder.

### Settings window

- New `TemplatesTab` (`@Component`, `SettingsTab`), built from the code of the `GeneralTab` field: label "Templates
  folder", text field, "Browse..." with a `DirectoryChooser` opened at the current folder when it exists.
  - `load` shows `fileTemplates.getUserTemplatesPath()`.
  - `validate` rejects an invalid path and a path that is not an existing directory; an empty field and the current
    folder pass. The path is made absolute and normalized first.
  - `save` calls `fileTemplates.setUserTemplatesPath(path)` (`null` for an empty field) on the shared
    `ExecutorService`, because it reads files; a failure is logged.
- `SettingsView` takes `TemplatesTab`; tab order: General, Templates, Scripts, MCP.
- `GeneralTab` returns to its state before `5012d57a`.
- Tab callbacks run on the FX thread; `setUserTemplatesPath` runs on the executor. The reload status message is
  published from that thread, as the watcher already does.

### Removed code

`UserFolder`, `UserFolderChangedEvent` and `UserFolderTest` are deleted. The setting tests move to
`UserTemplatesFolderTest`; `FileTemplatesTest` keeps loading and switching the folder.

## Risks / Trade-offs

- [The scripts change needs the same kind of setting] It adds its own small class next to the scripts code; a
  shared base is extracted only if a third folder setting appears.
- [A `userFolder.path` value stored by a build of the #1078 branch] It is ignored, and templates are read from the
  default folder. → Only development builds have it; the key stays in the properties file and does no harm.
- [Templates folder cannot be created at startup, e.g. a stored folder on a removed drive] → Logged; built-in
  templates load; the folder is watched again after it is changed in settings or after a restart.
- [Two quick saves with different folders] → `setUserTemplatesPath` and `UserTemplatesFolder.setPath` are
  `synchronized`; the last one wins and the setting, the watcher and the list stay consistent.

## Migration Plan

No data migration: users of released versions have no user folder setting, and the default templates folder is the
same as in stage 1. Rollback is a revert of this change.
