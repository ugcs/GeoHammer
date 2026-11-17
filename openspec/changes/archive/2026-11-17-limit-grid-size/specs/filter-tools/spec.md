## ADDED Requirements

### Requirement: Failed action shown in the tool

When the task of a tool's Apply or Apply to all action fails, the tool SHALL show the message of the failure in its
warning banner and SHALL NOT show a dialog. The banner SHALL NOT disappear by itself. It SHALL stay while the next
action of the tool runs, so that the tool's content does not move, and SHALL be hidden when that action succeeds;
when the action fails, its message SHALL replace the one shown. A warning that appears while an action runs SHALL
be kept when the action succeeds. A cancelled task SHALL NOT show a message. The tool's inputs and actions SHALL be
enabled as after a completed task.

#### Scenario: Action fails
- **WHEN** the user presses "Apply" in a tool and its task fails with a message
- **THEN** the tool's warning banner shows the message, and it is still shown a minute later

#### Scenario: Next action hides the message
- **WHEN** a tool shows the message of a failed action and the user presses "Apply" again
- **THEN** the message stays while the action runs and is hidden when it succeeds

#### Scenario: Next action fails too
- **WHEN** a tool shows the message of a failed action, the user presses "Apply" again and the action fails
- **THEN** the banner stays in place and shows the message of the last failure

#### Scenario: Warning shown while an action runs
- **WHEN** a warning appears in a tool while its action runs and the action succeeds
- **THEN** the warning is still shown

#### Scenario: Cancelled action
- **WHEN** the task of a tool's action is cancelled
- **THEN** no message is shown in the tool
