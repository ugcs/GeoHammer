## Why

Gridding needs a cell size and a blanking distance that match the survey line spacing, but the tool starts every new
template at 0.1 / 1.0 m and leaves finding suitable values to the user. A cell that is too small makes gridding slow and
noisy, a blanking distance below the line spacing leaves stripes of empty cells. GeoHammer can estimate both from the
track geometry of the selected file (#1016).

## What Changes

- A survey line spacing estimate computed from positions only: it detects lines from the track geometry and works when
  the file has no Line column and the whole track is a single line.
- An "Auto" button in the gridding tool, next to the cell size and blanking distance inputs, fills both inputs with
  values estimated from the selected file: cell size is a fifth of the line spacing, blanking distance equals the line
  spacing, both rounded to 2 decimal places and kept within the limits of the inputs.
- When the line spacing cannot be estimated, "Auto" leaves the inputs unchanged and the tool shows a warning that
  hides by itself.
- Tools of the right panel get a warning banner at the top of the tool that can hide itself after a few seconds; the
  gridding tool shows its "grid needs to be recalculated" warning in it.
- When a template has no saved gridding parameters and the selected file has no grid, the estimated values become the
  parameters of the template.
- Gridding parameters are saved for the template on "Apply" / "Apply to all" and when they are estimated as template
  defaults, and no longer when a palette, range or post-processing option changes.
- A new MCP tool returns the estimated gridding parameters and the line spacing of a file without running gridding.
- Two line spacing estimators are implemented and compared on synthetic and real surveys for accuracy, speed and
  memory; the one that works better is kept and the other is removed within this change.

## Non-goals

- Estimating from several files: "Apply to all" uses the values estimated from the selected file.
- Changing the parameters of the MCP gridding tool (`run_gridding`); the estimate is a separate tool.
- Re-estimating parameters for templates that already have saved values; the user presses "Auto" for them.
- Survey geometries without straight parallel lines (spirals, contour following): no estimate is made for them.
- Changing the upper limits of cell size and blanking distance.

## Capabilities

### New Capabilities
- `survey-geometry`: estimate of the survey line spacing from the track geometry of a data file.

### Modified Capabilities
- `gridding`: adds the "Auto" estimate of cell size and blanking distance, estimated template defaults, saving of
  parameters, and the MCP estimate tool.

## Impact

- New `SurveyService` (survey geometry analysis) with the line spacing estimate; `PrincipalComponents` gains the
  components of the steps of a path given as primitive arrays.
- `GriddingTool` (Auto button, estimate on file select, warnings in the banner), `FilterToolView` (warning banner),
  `GriddingParams` (estimate from the line spacing, limits, defaults), `GriddingSettings` (saved parameters or none),
  `Numbers` (rounding), `components.css` (flat button and warning banner styles).
- New MCP tool registered in `McpTools`.
- New `PrincipalComponentsTest`; the tests used to compare the estimators are not kept.
- No persisted format or dependency changes; settings keys stay the same.
