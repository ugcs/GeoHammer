# Tasks

## 1. User folder

- [x] 1.1 Add `UserFolder` (setting with `~/.geohammer` as the default, fallback for an invalid stored path,
  `getPath()`, `getTemplatesPath()`, `setPath(Path)` publishing `UserFolderChangedEvent`) and the event class; add
  `UserFolderTest` covering: templates subfolder, changed path stored and event published, same path ignored, `null`
  selects the default and removes the key; run `mvn -Dtest=UserFolderTest test`

## 2. Templates

- [x] 2.1 Make `FileTemplates` take `UserFolder`, turn the existing loader into `loadTemplates(Path)` and merge both
  folders in `loadTemplates()` (user templates first, built-in templates of the same name dropped), create the user
  templates folder in `afterPropertiesSet`; add `FileTemplatesTest` with temporary folders covering: folder created
  when missing, templates from both folders loaded, user templates listed before built-in ones, same-name built-in
  template excluded from the list and from `getTemplate(name)`, empty user folder keeps built-in templates, missing
  application folder, built-in template back after the user template is deleted, templates read from a changed user
  folder; run `mvn -Dtest=FileTemplatesTest test`
- [x] 2.2 Make the watcher follow the user templates folder: register the application folder and the current user
  templates folder, and on `UserFolderChangedEvent` create the folder, re-register and reload under one lock; verify
  `mvn test` passes and, in the running app, a yaml put into the user templates folder is picked up before and after
  the user folder is changed in settings

## 3. Settings window

- [x] 3.1 Add the "User folder" field with "Browse..." to `GeneralTab`: show the current folder, reject a path that
  is not an existing folder, treat an empty value as the default, apply on the executor; verify in the running app
  that the folder is shown, changed and kept after a restart

## 4. Docs

- [x] 4.1 Update `spec/overview.md`: the runtime folders paragraph, the events list and the "CSV templates" recipe;
  verify the text matches the `user-folder` and `csv-templates` specs

## 5. Verify

- [x] 5.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 5.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
