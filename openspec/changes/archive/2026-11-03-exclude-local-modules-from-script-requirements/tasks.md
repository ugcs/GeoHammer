# Tasks

## 1. Exclude local modules from generated requirements

- [x] 1.1 Add a public static method to `ScriptRequirements` that takes requirement lines and the script directory and
  returns the lines without entries matching a local module (`*.py` stem or a subdirectory with `__init__.py`),
  comparing PEP 503-normalized names and keeping blank lines and comments; verify by the tests in 1.3
- [x] 1.2 In `ScriptRequirements.runAnalyzer`, rewrite the generated file through that method after pipreqs finishes;
  verify the method is called on the file at the `--savepath` location
- [x] 1.3 Add `ScriptRequirementsTest` with a `@TempDir` covering the spec scenarios: sibling `script_utils.py` drops
  `script_utils` and keeps `pandas`; a package directory with `__init__.py` is dropped, one without it is kept;
  `Script-Utils` matches `script_utils.py`; comments and version specifiers are handled; verify with
  `mvn -Dtest=ScriptRequirementsTest test`

## 2. Reuse the existing requirements file on reinstall

- [x] 2.1 In `ScriptDependencies.installDependencies`, call `generateIfMissing` for both normal and forced installs and
  make `ScriptRequirements.generate` private; verify `generate` has no callers outside `ScriptRequirements`

## 3. Verify

- [x] 3.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 3.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference, fix critical and major issues, and report the scores and any issues left open
