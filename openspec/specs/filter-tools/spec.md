# filter-tools Specification

## Purpose

Defines how processing tools in the right panel (filters, gridding, quality control, GPR background and elevation)
behave while one of their tasks runs, so that a tool never runs two tasks at once and never applies a stale result,
and what the low-pass filter produces on each survey line.

## Requirements

### Requirement: Tool actions stay disabled while a task runs

While a tool's task runs, the tool's Apply and Apply to all actions SHALL be disabled. Selecting another file, another
series or a file of another template, or a change of the tool's input, SHALL NOT enable them before the task completes.

#### Scenario: Series changed during gridding
- **WHEN** gridding is running and the user selects another series of the same file
- **THEN** Apply and Apply to all of the gridding tool stay disabled until the gridding completes

#### Scenario: File of another template selected during a filter
- **WHEN** a low-pass filter is running and the user selects a file of another template whose saved filter length is
  valid
- **THEN** Apply and Apply to all of the low-pass tool stay disabled until the filter completes

### Requirement: Tool actions follow input validity when a task completes

When a tool's task completes, the tool's Apply and Apply to all actions SHALL be enabled only if the tool's current
input is valid.

#### Scenario: Input became invalid during the task
- **WHEN** a running median is running, the user selects a file of another template that has no saved window, and the
  running median completes
- **THEN** Apply and Apply to all of the running median tool stay disabled

#### Scenario: Input valid at completion
- **WHEN** a time lag completes and the tool's shift is valid
- **THEN** Apply and Apply to all of the time lag tool are enabled

### Requirement: Tool inputs stay disabled while a task runs

While a tool's task runs, the tool's inputs SHALL be disabled. Selecting another file SHALL NOT enable them before the
task completes.

#### Scenario: File selected during surface flattening
- **WHEN** surface flattening of a GPR file is running and the user selects another GPR file that has a ground profile
- **THEN** the inputs of the GPR elevation tool stay disabled until the flattening completes

### Requirement: No automatic regrid while gridding runs

When a time-lagged series that the current grid was built from is updated while the gridding tool is computing a grid,
the grid SHALL NOT be recomputed automatically; the warning that the grid needs to be recalculated SHALL be shown
instead and SHALL remain shown after the running gridding completes. When no gridding is running, the grid SHALL be
recomputed automatically as before.

#### Scenario: Time lag applied during gridding
- **WHEN** the grid of a time-lagged series is shown, the user starts gridding, and a time lag is applied to the same
  series before the gridding completes
- **THEN** no second gridding starts, and after the running gridding completes the grid is shown with the warning that
  it needs to be recalculated

#### Scenario: Time lag applied with no gridding running
- **WHEN** the grid of a time-lagged series is shown, no gridding is running, and a time lag is applied to the same
  series
- **THEN** the grid is recomputed automatically

### Requirement: Low-pass filter keeps short lines and length 1 valid

The low-pass filter SHALL filter each line separately. On a line it SHALL use the entered filter length, limited to the
number of measurements in the line. A filter length of 0 or 1 SHALL leave the values of the line unchanged. The filter
SHALL NOT produce NaN values for any filter length or line length.

#### Scenario: Filter length 1
- **WHEN** the low-pass filter with length 1 is applied to a series
- **THEN** the `_LPF` series has the same values as the source series

#### Scenario: Line of three measurements
- **WHEN** a file has a line of 3 measurements 1190, 1191, 1192 and the low-pass filter with length 100 is applied
- **THEN** the `_LPF` values of that line are 1190, 1191, 1192 and none of them is NaN

#### Scenario: Line shorter than the filter length
- **WHEN** the low-pass filter with a length of 1000 is applied to a file whose lines have 10 to 500 measurements
- **THEN** every line of the `_LPF` series has numeric values and no NaN

### Requirement: Low-pass filter keeps the level and position of the series

The low-pass filter SHALL keep the level of the series: a constant line SHALL stay the same constant, and the filter
SHALL NOT rescale the filtered values. The filter SHALL NOT shift the series along the line for even or odd filter
lengths: a feature symmetric around a measurement SHALL stay symmetric around the same measurement.

#### Scenario: Constant line
- **WHEN** the low-pass filter with length 8 is applied to a line whose values are all 50000
- **THEN** all `_LPF` values of the line are 50000

#### Scenario: Spike with an even filter length
- **WHEN** the low-pass filter with length 4 is applied to a line of 11 measurements that are 0 except a value of 10 at
  the sixth measurement
- **THEN** the largest `_LPF` value is at the sixth measurement, the values at the fifth and seventh measurements are
  equal, and the sum of the `_LPF` values of the line is 10

#### Scenario: Spike with an odd filter length
- **WHEN** the low-pass filter with length 5 is applied to the same line
- **THEN** the largest `_LPF` value is at the sixth measurement, values at equal distances before and after it are
  equal, and the sum of the `_LPF` values of the line is 10

### Requirement: Low-pass filter keeps linear trends at line ends

The low-pass filter SHALL extend each line end by mirroring the line around its end value (point reflection), so that a
linear trend continues through the end. When the value at a line end is missing, the nearest available value of the
line SHALL be used as the end value. A line with no values SHALL stay without values.

#### Scenario: Ramp
- **WHEN** the low-pass filter with length 4 or 8 is applied to a line of 10 measurements 1190, 1191, ..., 1199
- **THEN** the `_LPF` values of the line are 1190, 1191, ..., 1199

#### Scenario: Missing value at the end of a line
- **WHEN** the low-pass filter with length 8 is applied to a line of 40 measurements whose last value is missing
- **THEN** every `_LPF` value of the line is a number and none is NaN

#### Scenario: Line with no values
- **WHEN** the low-pass filter is applied to a series that has no values on one of its lines
- **THEN** the `_LPF` series has no values on that line and the other lines are filtered
