## Context

See proposal.md for motivation. Before this change `AgentController` built each agent command as one string
(`claude mcp get geohammer`) and ran it through `$SHELL -lc` (`cmd /c` on Windows). The status is derived from the
exit code: 0 is enabled, 127/9009 or an `IOException` is "not available", anything else is disabled.

Constraints that shape the approach:

- A login, non-interactive shell does not read `.zshrc`/`.bashrc`, where installers commonly add their PATH line.
- On Windows the child inherits the PATH GeoHammer started with; an install made while the app is open is invisible.
- On Windows npm installs `claude.cmd`/`codex.cmd` next to a script without an extension; the native installers
  install `.exe`.

## Goals / Non-Goals

**Goals:**

- Find an agent installed in a usual install folder that the shell does not resolve.
- Keep the change small: command execution, the status model and the settings view stay as they are.

**Non-Goals:**

- See proposal.md. In particular no manual "Locate…", no new states, no time limits.
- Moving `PythonInterpreter` lookup onto the new utility methods.

## Decisions

### 1. Only the executable in the command changes

`AgentController` subclasses return the executable name (`claude`, `codex`) and the arguments of each command
separately. `AgentController` resolves the executable to an absolute path and puts it in place of the name; the
command still runs as one string through the same shell wrapper and `CommandExecutor.executeCommand`, and the exit
code is mapped to a status as before. A path containing a space is wrapped in double quotes.

Alternative considered: run the executable directly with an argument list and no shell. Rejected for this change: it
replaces the execution path and needs its own handling of `.cmd` files and of the child PATH for script agents.

### 2. Lookup order: shell first, install folders second

`AgentController.resolveExecutable()` asks the shell; `ClaudeAgent` and `CodexAgent` override it to fall back to their
install folders when the shell finds nothing.

1. `OperatingSystemUtils.resolveExecutable(name)` runs `which <name>` (`where <name>` on Windows) through the same
   shell wrapper as the agent commands and returns the first output line that is an absolute path of an executable
   file. Lines that are not such a path (profile output, alias descriptions) are skipped.
2. `OperatingSystemUtils.findExecutable(name, directories)` returns the first executable file with the name in the
   given folders, in order. On Windows a name without an extension is tried as `.exe`, then `.cmd`.

The shell goes first so the app uses the same agent as the user's terminal; the folders only cover an agent the shell
does not resolve.

Install folders (`~` is the user home; folders that do not exist on the current OS simply miss):

| Agent | Folders |
|---|---|
| Claude Code | `~/.local/bin`, `~/.claude/local`, `/opt/homebrew/bin`, `/usr/local/bin`, `~/.npm-global/bin`, `~/AppData/Roaming/npm`, `~/AppData/Local/Microsoft/WinGet/Links` |
| Codex | `~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`, `~/.npm-global/bin`, `~/AppData/Roaming/npm` |

Alternative considered: install folders first. Rejected: it saves one shell start per session but may pick a stale
copy that differs from the one the terminal uses.

### 3. The resolved path is kept until a command fails to start

The resolved path is kept in `AgentController` and reused by the following commands. It is dropped when a command
cannot be started or exits with the shell's "not found" code, so the next command resolves again. An agent that is
not found is looked up again on every command.

### 4. Shell command wrapping moves to `OperatingSystemUtils`

`toShellCommand` moves from `AgentController` to `OperatingSystemUtils` unchanged, since the lookup needs it too.
`OperatingSystemUtils.resolveExecutable` starts its own short process and reads the output in place instead of using
the `CommandExecutor` bean, which a static utility cannot reach.

### Threads and events

Lookup and agent commands run on the shared virtual-thread executor through the existing `AgentController.submit`,
one task at a time per agent. `status` and `busy` are set on the FX thread with `Platform.runLater`. No Spring events
are added or changed; no file data is touched.

## Risks / Trade-offs

- Install folders drift as the agents change their installers → the lists are one constant per agent.
- An npm-installed agent found only in an install folder is a script that needs `node`; when the shell does not
  resolve `node` either, the command exits with "not found" and the agent stays "Not available".
- A different program named `claude` or `codex` in an install folder would be run → same trust level as PATH
  resolution; the folders are the agents' own install targets.
- The shell lookup has no time limit, like the agent commands; a shell profile that hangs keeps the agent busy.
- Windows folders are built from the user home, not from `%APPDATA%`/`%LOCALAPPDATA%`; a redirected profile is missed.
- The Windows paths (`where` output, quoting through `cmd /c`) are not verified on a Windows machine. For an npm
  install `where` lists the script without an extension before its `.cmd` launcher; the first line is used as is.
