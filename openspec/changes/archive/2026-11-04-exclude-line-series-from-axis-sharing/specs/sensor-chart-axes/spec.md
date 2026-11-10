## Purpose

Defines how the sensor chart chooses the vertical axis range of each series and when series share one axis, so that
series with similar values can be compared on one scale while each series stays readable.

## ADDED Requirements

### Requirement: Series with similar values share a vertical axis
Each series in a sensor chart SHALL have a vertical axis that covers all its finite values, with bounds aligned to round
tick values. Two series SHALL be similar when the value ranges they cover with outlier spikes left out overlap by at
least half of their combined range. Series similar to each other, directly or through other similar series, SHALL share
one vertical axis that covers all finite values of every series in the group, outlier spikes included. The groups SHALL
be formed when the chart is opened or reloaded and when a filtered series is added.

#### Scenario: Similar series share an axis
- **WHEN** a file is opened with series A ranging 0..10 and series B ranging 2..12
- **THEN** A and B are shown on one vertical axis covering 0..12

#### Scenario: A spike does not prevent sharing
- **WHEN** a file is opened with series A ranging 0..10 and series B that ranges 2..12 except a single spike of 500
- **THEN** A and B are shown on one vertical axis covering 0..500

#### Scenario: Dissimilar series keep their own axes
- **WHEN** a file is opened with series A ranging 0..10 and series B ranging 50..60
- **THEN** A is shown on an axis covering 0..10 and B on an axis covering 50..60

### Requirement: Line series keeps its own vertical axis
The series with the Line semantic SHALL never share a vertical axis with other series: its axis SHALL cover only its
own values. The Line series SHALL NOT join other series into a group: series that are similar only through the Line
series SHALL keep their own axes.

#### Scenario: Line series next to a similar series with a spike
- **WHEN** a MagNIMBUS file is opened whose `Next WP` line series ranges 0..16 and whose `GyroVect` series ranges
  mostly 0..19 with a single spike of 399
- **THEN** `Next WP` is shown on an axis covering 0..16 and `GyroVect` on an axis covering 0..400

#### Scenario: Series similar only through the Line series
- **WHEN** a file is opened with a Line series ranging 0..12, series A similar to the Line series and series B similar
  to the Line series but not to A
- **THEN** the Line series, A and B are each shown on their own axis

#### Scenario: Line series stays on its own axis after reload
- **WHEN** lines of a file are split, renumbering the Line series to 0..20, and the chart is reloaded
- **THEN** the Line series is shown on an axis covering 0..20, not shared with other series
