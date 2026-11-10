# Spec Delta

## REMOVED Requirements

### Requirement: A reload does not overwrite concurrent edits
**Reason**: The reload path is simplified to a single replace of the open file; edits made while the reload is read are
lost as edits made before choosing "Reload".
**Migration**: None. Avoid editing a file while its reload is in progress.
