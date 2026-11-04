# Design

## Context

`ScriptDependencies.installDependencies` gets a requirements path from `ScriptRequirements`:
`generateIfMissing` on a normal run, `generate` on a forced reinstall. `ScriptRequirements.runAnalyzer` copies only
the script into a temporary directory and runs `pipreqs --mode no-pin --savepath <script>_requirements.txt` on it.
pipreqs treats as local only the modules it finds in the scanned directory, so a sibling `script_utils.py` is
resolved against PyPI. Everything runs on the script task's background thread; no FX thread, events, file data,
undo or caches are involved.

## Goals / Non-Goals

**Goals:**
- Fix the root cause for any script with sibling modules, not just `script_utils`.
- Keep the filter testable without Python or pipreqs.

**Non-Goals:**
- Changing how imports are verified (`check_imports.py`) or how pip is invoked.

## Decisions

### Filter the analyzer output by the script's directory

After pipreqs writes the file, `ScriptRequirements` rewrites it without the entries whose normalized name matches a
local module of the script's directory. Local modules are the stems of `*.py` files and the names of
subdirectories that contain `__init__.py`. Normalization follows PEP 503: lowercase, runs of `-`, `_`, `.` collapse to
`-`. An entry's name is the line up to the first version specifier, extra or marker character, so the filter stays
correct if the mode ever changes from `no-pin`; blank lines and comments are kept as they are.

The filtering is a public static method on `ScriptRequirements` taking the requirement lines and the script directory
and returning the kept lines. It builds a result and mutates nothing, so it is unit-tested with a `@TempDir` and real
files.

Alternatives:
- Copy sibling modules into the temporary directory so pipreqs recognizes them itself. Copying all of them makes
  pipreqs report the union of every script's imports; copying only the imported ones needs import parsing in Java.
  More code for the same result.
- Hard-code `script_utils` as an exclusion. Fixes the symptom; the next shared helper breaks the same way.
- pipreqs `--ignore`. It ignores directories to scan, not module names, so it does not apply.

### Reinstall uses `generateIfMissing`

`ScriptDependencies.installDependencies` calls `generateIfMissing` on both paths; the forced path differs only in
skipping the initial import check and calling `reinstallFromRequirements`. `ScriptRequirements.generate` then has no
outside caller and becomes private. A bundled requirements file is the curated source of truth, and regenerating it
on every reinstall also costs a pipreqs run with PyPI lookups.

## Risks / Trade-offs

- [A sibling module shadows a real PyPI dependency with the same name] → the dependency is dropped. This matches
  Python's import behavior: the script directory is first on `sys.path`, so the local module wins anyway.
- [Dependencies of a local module are not detected] → out of scope (see proposal Non-goals); bundled scripts import
  them directly.
- [A reinstall no longer picks up imports added to an edited script] → the user deletes the script's requirements
  file to have it regenerated. Acceptable: bundled files ship with the scripts they belong to.
- [Files already polluted by an earlier version keep `script_utils`] → bundled files are replaced on update; a user's
  own file can be deleted.
