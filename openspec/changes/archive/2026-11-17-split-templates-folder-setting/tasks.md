# Tasks

## 1. Templates folder setting

- [x] 1.1 Make `FileTemplates` take `Settings` instead of `UserFolder`: read the user templates folder from the
  `userTemplates.path` setting in the constructor (empty → `~/.geohammer/templates`, invalid path → warning and
  default), add `getUserTemplatesPath()` and `synchronized setUserTemplatesPath(@Nullable Path)` (null → default,
  same folder → no-op, otherwise store the setting, create the folder, re-register the watcher, reload), and remove
  `onUserFolderChanged`; verify the project compiles
- [x] 1.2 Move `FileTemplatesTest` to `Settings` (the user folder is set in `Settings` before the bean is built) and
  add tests: default folder when nothing is stored, default folder for an invalid stored value, changed folder stored
  and templates reloaded from it, folder created on change, same folder ignored, `null` selects the default and
  removes the key; run `mvn -Dtest=FileTemplatesTest test`
- [x] 1.3 Delete `UserFolder`, `UserFolderChangedEvent` and `UserFolderTest`; verify no references remain and the
  project compiles
- [x] 1.4 Move the setting out of `FileTemplates` into a new `UserTemplatesFolder` bean (`getPath()`,
  `synchronized setPath(@Nullable Path)`, default and invalid-value fallback, no file IO); make `FileTemplates` take
  it and keep `getUserTemplatesPath()` / `setUserTemplatesPath()` delegating to it before switching the watcher and
  reloading; move the setting tests to `UserTemplatesFolderTest` (default, invalid value, change stored, same path
  ignored, `null` selects the default and removes the key) and keep loading and folder switching in
  `FileTemplatesTest`; run both tests

## 2. Settings window

- [x] 2.1 Add `TemplatesTab` with the "Templates folder" field and "Browse...", moving load, validate and save from
  `GeneralTab` (save calls `setUserTemplatesPath` on the shared executor), register it in `SettingsView` after
  "General", and restore `GeneralTab` to its state before `5012d57a`; verify the project compiles and `GeneralTab`
  has no diff against `5012d57a^`

## 3. Docs

- [x] 3.1 Update `spec/overview.md`: the runtime folders paragraph (templates folder setting, default
  `~/.geohammer/templates`), drop `UserFolderChangedEvent` from the events list, and the "CSV templates" recipe
  (folder from `FileTemplates.getUserTemplatesPath()`, changed on the "Templates" tab); verify no `UserFolder`
  mention is left in `spec/`

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
