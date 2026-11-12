## Why

A demo opened from the welcome panel sends two events: `geohammer-demo-file-opened` and the regular
`geohammer-file-opened`. The regular event has no sign of a demo, so file opening statistics by template include demo
clicks and cannot be filtered (#1014).

## What Changes

- `geohammer-file-opened` and `geohammer-file-open-error` carry a `demo` property: `true` for a demo file opened from
  the welcome panel, `false` for any other file.
- **BREAKING** The separate `geohammer-demo-file-opened` event is no longer sent.

## Non-goals

- Reporting which demo was opened beyond the template the events already carry.
- Sending an event when a demo file is missing or cannot be copied (no file is opened in that case).

## Capabilities

### New Capabilities

### Modified Capabilities
- `usage-analytics`: the demo file opening event is replaced by a `demo` property on file opening events.

## Impact

- `EventType.DEMO_FILE_OPENED` and `EventsFactory.createDemoFileOpenedEvent` removed; `DemoService` no longer sends
  events.
- `DemoFiles.isInDemoFolder` tells whether a file lies in the folder of demo copies; `FileOpenEventsAnalytics` passes
  it to `EventsFactory.createFileOpenedEvent` / `createFileOpenErrorEvent`.
- Analytics dashboards built on `geohammer-demo-file-opened` need the `demo` filter instead.
