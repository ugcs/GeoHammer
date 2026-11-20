# Spec Delta

## Purpose

Estimates the sensor time lag of a data file from survey lines flown in opposite directions and writes the
lag-corrected series, so that users can correct a lag they do not know.

## ADDED Requirements

### Requirement: Lag-corrected series

The Auto Time Lag Correction script SHALL write the selected series shifted by the estimated number of measurements
to the column `<series>_LAG`, with the same semantics as the time lag tool: the shift is applied within each file line,
and values shifted past a line end are left empty. When no reliable estimate is found, the file SHALL NOT be changed.

#### Scenario: Estimate found
- **WHEN** the script estimates a shift of −22 measurements for series TMI
- **THEN** the file gets column `TMI_LAG` holding TMI shifted by −22 measurements within each file line

#### Scenario: No reliable estimate
- **WHEN** the script finds no reliable estimate
- **THEN** it exits with an error and the file is unchanged

### Requirement: Estimate from reciprocal segment pairs

The lag SHALL be estimated from pairs of adjacent straight track segments flown in opposite directions, detected from
the track geometry. Each pair SHALL give its own estimate. Pairs whose estimate disagrees with most pairs of the same
orientation SHALL NOT change the result. Biases of oblique anomalies that are opposite for the two pair orientations
SHALL cancel.

#### Scenario: Known lag on a serpentine survey
- **WHEN** a serpentine survey of parallel lines with scattered dipole anomalies is measured with a known lag
- **THEN** the estimate is within 4 measurements of the known lag

#### Scenario: One dominant anomaly
- **WHEN** a survey with a known lag has one anomaly much stronger than all others
- **THEN** the estimate is within 4 measurements of the known lag

#### Scenario: Pairs matching different anomalies
- **WHEN** fewer than half of the pairs of each orientation have their cost minimum far from the known lag
- **THEN** the estimate is within 4 measurements of the known lag

#### Scenario: Oblique anomaly
- **WHEN** a survey with a known lag contains a linear anomaly oblique to the survey lines
- **THEN** the estimate is within 4 measurements of the known lag

### Requirement: Reliability of the estimate

An estimate SHALL be reported as reliable only when at least two pairs (or the only pair with a distinct cost minimum),
including at least one pair of each orientation that has pairs, agree with it within half a meter along track.
Otherwise the script SHALL exit with an error stating that the lag cannot be determined reliably.

#### Scenario: Survey without anomalies
- **WHEN** the selected series holds only noise
- **THEN** the script exits with the error that the lag cannot be determined reliably

#### Scenario: Real survey with outlier pairs
- **WHEN** a survey has consistent pairs of both orientations along with pairs that match different anomalies
- **THEN** an estimate is reported

### Requirement: Uncertainty and diagnostics

With the estimate, the script SHALL print its uncertainty in measurements and in meters along track, the number of
pairs used, the estimate of each pair orientation, and how many pairs agree with the result.

#### Scenario: Estimate printed
- **WHEN** an estimate is found
- **THEN** the output shows the shift with a ± uncertainty in measurements and meters, the per-orientation estimates
  and the number of agreeing pairs out of all pairs

### Requirement: Single pair orientation

When only one pair orientation has pairs, the estimate SHALL come from that orientation alone, and the script SHALL warn
that oblique anomalies may bias the result.

#### Scenario: Two survey lines
- **WHEN** the track has only two reciprocal straight segments
- **THEN** an estimate is reported with the warning that oblique anomalies may bias the result
