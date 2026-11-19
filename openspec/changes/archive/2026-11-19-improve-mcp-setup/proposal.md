## Why

Connecting Claude Code or Codex to GeoHammer (Settings → AI Integration) relies on the agent command being resolvable
by the user's shell. When the agent is installed but not on the PATH GeoHammer sees — a common outcome of the agents'
own installers, of launching GeoHammer from Finder, or of installing the agent while GeoHammer is open on Windows —
the user gets a greyed "Not available" button although the agent is installed (#1018).

## What Changes

- GeoHammer finds the agent executable itself before running an agent command: first the way the user's shell
  resolves the agent name, then in the agent's usual install folders.
- Agent commands are run by the full path of the found executable, so integration works when the agent is installed
  in a usual install folder that is not on the PATH.

## Non-goals

- Changing how agent commands are executed, their status mapping or the settings view.
- Locating the executable manually or remembering its location between sessions.
- A separate "not found" state with an install guide; a missing agent stays "Not available".
- Time limits for agent commands.
- Changing the manual command offered for copying.
- Writing the agents' configuration files directly; registration stays with the agent CLI.
- Installing Claude Code or Codex on behalf of the user.
- Supporting other MCP clients (Claude Desktop chat, Cursor, VS Code).

## Capabilities

### New Capabilities

- `agent-integration`: connecting AI agents (Claude Code, Codex) to the GeoHammer MCP server from the settings
  window — finding the agent, showing its integration status, enabling and disabling the integration, manual setup.

### Modified Capabilities

None.

## Impact

- `settings`: `AgentController`, `ClaudeAgent`, `CodexAgent`.
- `util`: `OperatingSystemUtils` gains the shell command wrapping (moved from `AgentController`) and two executable
  lookups, with unit tests for the folder lookup.
- No new dependencies, no settings changes, no UI changes.
