## 1. Executable lookup

- [x] 1.1 Move `toShellCommand` from `AgentController` to `OperatingSystemUtils` unchanged; verify it compiles
- [x] 1.2 Add `OperatingSystemUtils.resolveExecutable(name)`: `which`/`where` through the shell wrapper, first output
      line that is an absolute path of an executable file; verify with
      `$SHELL -lc "which claude"` returning the path the method is expected to return
- [x] 1.3 Add `OperatingSystemUtils.findExecutable(name, directories)`: first executable file in the folders in
      order, `.exe` then `.cmd` on Windows for a name without an extension; verify it compiles
- [x] 1.4 Add `OperatingSystemUtilsTest` for `findExecutable` with temp folders: later folder, folder order, name
      with an extension, not found, file without execute permission; verify with
      `mvn -Dtest=OperatingSystemUtilsTest test`

## 2. Agent controller

- [x] 2.1 Split the commands of `AgentController`, `ClaudeAgent`, `CodexAgent` into the executable name and
      arguments, keeping `getEnableCommand()` for the copyable command; verify the copied text is unchanged
- [x] 2.2 Resolve the executable in `AgentController` before running a command and run it by its absolute path,
      quoted when it contains a space; verify a quoted path with a space runs through `$SHELL -lc`
- [x] 2.3 Fall back to the install folders in `ClaudeAgent` and `CodexAgent` when the shell lookup finds nothing;
      verify the folder lists against design decision 2
- [x] 2.4 Drop the kept executable path when a command cannot be started or exits with "not found", and log a failed
      lookup at `warn`; verify with `mvn test`

## 3. Verify

- [ ] 3.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings
- [x] 3.2 If the `code-review` skill is available, invoke it (not the `code-review:code-review` plugin) on the
      uncommitted changes with this change's proposal and specs as the completeness reference, fix critical and
      major issues, and report the scores and any issues left open
