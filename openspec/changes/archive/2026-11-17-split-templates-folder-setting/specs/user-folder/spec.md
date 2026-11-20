# Spec Delta

## REMOVED Requirements

### Requirement: User folder has a default location

**Reason**: Each kind of user files gets its own folder setting instead of a subfolder of one shared folder.
**Migration**: User templates are read from the folder in "Templates folder" on the "Templates" tab,
`.geohammer/templates` in the home directory by default, as specified in `csv-templates`.

### Requirement: User folder is shown and changed in settings

**Reason**: The shared "User folder" field on the "General" tab is replaced by a per-kind folder field.
**Migration**: Use "Templates folder" on the "Templates" tab (`csv-templates`). A user folder stored before was never
released and is not carried over.

### Requirement: Invalid user folder is rejected

**Reason**: Replaced by the same rule for the templates folder.
**Migration**: See "Invalid templates folder is rejected" in `csv-templates`.

### Requirement: Other application files stay in place

**Reason**: Replaced by the same rule for the templates folder.
**Migration**: See "Files of a previous templates folder stay in place" in `csv-templates`.
