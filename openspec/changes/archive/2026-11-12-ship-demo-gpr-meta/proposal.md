## Why

The GPR demo file is replaced with a survey whose line structure and display settings (contrast, gain, background
removal, depth range) live in its `.geohammer` sidecar. A demo copies only the data file, so the sidecar is lost and the
demo opens as one continuous, unprocessed profile.

## What Changes

- The shipped GPR demo file is replaced, and its sidecar is shipped next to it in `data/`.
- The shipped CSV demo file is replaced with a newer MagNIMBUS survey; it still opens with the MagNIMBUS template.
- A demo copies the sidecar of the demo file together with the file when the sidecar is shipped.
- Each click restores the shipped sidecar, so line and display edits saved in a demo are dropped as before.
- The GPR demo is renamed from "Gas Pipes GPR Demo" to "GPR Grid Survey Demo", since it is no longer the Quick Start
  gas pipelines survey.
- A `geohammer-demo-file-opened` analytics event is sent when a demo is opened, so demo usage can be told apart from
  opening user files.

## Non-goals

- Changing the demo links or the CSV demo title.
- Shipping a sidecar for the CSV demo (CSV files keep their state in the file itself).

## Capabilities

### New Capabilities

### Modified Capabilities
- `welcome-screen`: the GPR demo is a grid survey with a new title, and a demo opens with the line structure and
  display settings shipped with the demo file.
- `usage-analytics`: a demo file opening event.

## Impact

- `DemoFile.GPR` title changed.
- `EventType.DEMO_FILE_OPENED`, `EventsFactory.createDemoFileOpenedEvent`; `DemoService.openDemo` sends the event with
  the demo file name on its background thread after the loader reports the demo copy as opened.
- `DemoFiles.createCopy` copies the sidecar resolved by `MetaFiles.getMetaPath` when it exists.
- `data/demo-gpr.sgy` and `data/demo-csv.csv` replaced, `data/demo-gpr.sgy.geohammer` added; packaging already copies the whole `data/` folder.
- No persisted format, settings or dependency changes.
