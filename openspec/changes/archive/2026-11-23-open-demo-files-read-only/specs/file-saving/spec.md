## MODIFIED Requirements

### Requirement: Save to own path of a CSV-type file

When "Save to.." on a CSV-type file targets the path the file is currently open from, the system SHALL save the file
in place with the same result as "Save" and SHALL NOT report an error. A demo file shipped with the app is the
exception: saving it to its own path SHALL be refused with a message that the demo file is read-only, and the file
SHALL stay unchanged.

#### Scenario: Accepting the suggested path
- **WHEN** the user opens a CSV-type file, edits it, chooses "Save to.." and confirms the suggested name and folder
- **THEN** the file is written to its own path with the edits and is no longer marked unsaved
- **AND** the chart stays bound to the same file and no error is shown

#### Scenario: Per-file state is kept
- **WHEN** a CSV-type file with a computed grid is saved to its own path with "Save to.."
- **THEN** the grid of that file is still shown after the save

#### Scenario: Demo file to its own path
- **WHEN** the user opens the CSV demo, edits it, chooses "Save to.." and selects the demo file's own path
- **THEN** nothing is written and the message says the demo file is read-only
