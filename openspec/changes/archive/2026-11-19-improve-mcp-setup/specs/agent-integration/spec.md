## Purpose

Defines how AI agents (Claude Code, Codex) are connected to the GeoHammer MCP server from the AI Integration settings:
how an installed agent is found, how its integration status is shown, and how the integration is enabled, disabled
or set up manually.

## ADDED Requirements

### Requirement: Installed agent is found in its usual install folders

The app SHALL look for the agent executable first the way the user's shell resolves the agent's command name, then in
the usual install folders of the agent, and SHALL use the first one found. The app SHALL treat the agent as found
when it is installed in a usual install folder that the shell does not resolve.

#### Scenario: Agent resolved by the shell
- **WHEN** the user's shell resolves the agent's command name
- **THEN** the agent is found at the location the shell reports

#### Scenario: Agent installed outside the PATH
- **WHEN** the shell does not resolve the agent's command name and the agent executable is in a usual install folder
- **THEN** the agent is found and its integration can be enabled

#### Scenario: Agent installed in a folder with a space in its path
- **WHEN** the agent executable is found in a folder whose path contains a space
- **THEN** the agent's status is shown and its integration can be enabled and disabled

#### Scenario: Agent installed while the app is open
- **WHEN** the agent was not available, the user installs it to a usual install folder and the status is checked again
- **THEN** the agent is found without restarting the app

#### Scenario: Agent moved while the app is open
- **WHEN** the agent was found, is then reinstalled to another usual install folder, and the status is checked twice
- **THEN** the agent is found at the new location without restarting the app

#### Scenario: Agent not installed
- **WHEN** the shell does not resolve the agent's command name and no agent executable exists in the usual install
  folders
- **THEN** the agent is shown as not available

### Requirement: Integration status is shown per agent

For each agent the settings SHALL show one of the states: enabled, disabled, not available. The status SHALL be
checked when the settings are loaded into the settings window and after every successful enable and disable action. While an
action is running the agent's control SHALL be disabled.

#### Scenario: Integration enabled
- **WHEN** the agent is found and GeoHammer is registered in it
- **THEN** the agent shows a Disable action

#### Scenario: Integration disabled
- **WHEN** the agent is found and GeoHammer is not registered in it
- **THEN** the agent shows an Enable action

#### Scenario: Agent not available
- **WHEN** the agent is not found or its status command cannot be started
- **THEN** the agent is shown as not available and neither Enable nor Disable is offered

### Requirement: Integration is enabled and disabled through the agent

Enabling SHALL register the GeoHammer MCP server address in the agent's user-level configuration using the agent's
own command; disabling SHALL remove that registration the same way. The app SHALL NOT edit the agent's configuration
files itself.

#### Scenario: Enable
- **WHEN** the integration is disabled and the user uses Enable
- **THEN** GeoHammer is registered in the agent and the status becomes enabled

#### Scenario: Disable
- **WHEN** the integration is enabled and the user uses Disable
- **THEN** the registration is removed from the agent and the status becomes disabled

### Requirement: Failed agent commands are reported

When an enable or disable command fails, the app SHALL show an error with the output of the agent command and SHALL
keep the previous status.

#### Scenario: Enable fails
- **WHEN** the user uses Enable and the agent command exits with an error
- **THEN** an error with the command output is shown and the status stays disabled

### Requirement: Manual setup command is offered for copying

For each agent the settings SHALL offer the enable command for copying. The command SHALL start with the agent's
command name, whether or not the agent is found.

#### Scenario: Command copied
- **WHEN** the user copies the manual command of an agent
- **THEN** the copied command starts with the agent's command name and registers the GeoHammer MCP server address
