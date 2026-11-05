## Why

With no files open the chart area is blank, so a new user does not learn what the app can open, that files and folders
can be dropped onto the window, or where to get data to try it (#1014). The Quick Start tells users to download sample
data from file shares first, which does not work in the field without internet.

## What Changes

- While no file is open, the chart area shows a welcome panel instead of a blank space. It disappears when a file is
  opened and comes back when the last file is closed.
- The panel tells the user to drop files or folders onto the chart area; opening via the toolbar button is unchanged.
- The panel lists the supported file extensions, taken from `FileTypes`, in two columns: GPR data and sensor data.
- Each column has a demo button that opens a demo file shipped with the app: the Gas pipelines GPR survey used in the
  Quick Start and Setting marks articles, and the MagNIMBUS single-sensor magnetometer survey used in the Automated
  magnetic anomaly marking article. The full files are shipped, not trimmed copies.
- A demo opens a fresh copy of the demo file in the user folder, so the shipped demo file is never modified and every demo
  starts from the original data.
- Demos work offline: the demo files are part of every installer and the Linux zip.
- The panel links to the wiki: Quick Start, Template Format Specification and AI Integration.
- While files are dragged over the chart area, the drop zone on the panel is highlighted.

## Non-goals

- A modal welcome window or a "show at startup" setting: the panel is an empty state, not a dialog.
- Trimming or re-processing the demo files, or adding more demos than one GPR and one CSV demo file.
- Downloading demo files on demand or storing them in Git LFS; the files are committed to the repository.
- Changing how files are opened, detected or matched to templates; the panel uses the existing open flow.
- Changing the toolbar, the map or the right tool panel while no file is open.

## Capabilities

### New Capabilities
- `welcome-screen`: the panel shown in the chart area while no file is open: its visibility, file drop hint, list of
  supported formats, demo files and documentation links.

### Modified Capabilities

## Impact

- UI: the center area of `ProfileView` gets the welcome panel next to the charts container; new styles in the
  components stylesheet. `FileTypes` exposes the supported extensions read-only for the panel.
- New runtime folder `demo/` with the two demo files (48 MB GPR + 31 MB CSV, ~79 MB), resolved like `templates/` and
  `scripts/`; the repository grows by the size of the demo files once.
- Packaging: `pom.xml` copies `demo/` into the installer input and the target folder; `assembly/linux/zip.xml` adds it
  to the Linux zip. Installer size grows by the demo files size on every platform.
- Demo copies are written to `~/.geohammer/demo/`.
- `spec/overview.md`: runtime folders list gains `demo/`.
