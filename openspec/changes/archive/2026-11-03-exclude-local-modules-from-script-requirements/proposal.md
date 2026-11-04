# Proposal

## Why

The requirements file GeoHammer generates for a script lists `script_utils`, the shared helper module bundled next to
the scripts, as if it were a PyPI package. When dependencies are installed from that file, pip installs the unrelated
public package `script-utils` into the user's Python environment: clutter and a supply-chain risk (#1071).

The analyzer sees only a temporary copy of the script, so it cannot tell that `script_utils` is local. The bad file is
also persisted: forcing a dependency reinstall regenerates the requirements file and overwrites the one bundled with the
script, so every later run reuses the polluted file.

## What Changes

- A generated requirements file no longer lists modules that live in the script's directory (a `<name>.py` file or a
  `<name>/` package next to the script). Names are compared after PyPI name normalization, so `script_utils` and
  `script-utils` match.
- A forced dependency reinstall uses the existing requirements file of the script and no longer regenerates or
  overwrites it. A requirements file is generated only when the script has none.

## Non-goals

- Uninstalling `script-utils` from environments where it is already installed. It is a third-party package in the
  user's environment; the workaround in #1071 covers it.
- Rewriting requirements files that already list a local module. Bundled files never did and are replaced on update;
  a user's own polluted file can be deleted to have it regenerated.
- Detecting transitive dependencies of local modules (for example `pandas` imported by `script_utils`). The bundled
  scripts that use `script_utils` import their dependencies themselves.

## Capabilities

### New Capabilities

### Modified Capabilities
- `script-execution`: adds requirements on which packages a generated requirements file lists and on reusing the
  existing requirements file when dependencies are reinstalled.

## Impact

- `service/script`: `ScriptRequirements` (filtering of the generated file), `ScriptDependencies` (reinstall path).
- No new dependencies, no changes to bundled scripts or their requirements files.
