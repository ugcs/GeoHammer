# Proposal

## Why

The Auto Time Lag Correction script (#1087) combines all segment pairs of one orientation into a single
energy-weighted cost, so one strong anomaly or a few pairs that match different anomalies decide the estimate. On the
Brandenburg QuSpin file, 6 of 11 pairs have false minima 0.5–3.5 m away. Those pairs pull the estimate to −29
measurements, while the consistent pairs agree on about −22. On semi-synthetic data with a known lag, a single
dominant target gave errors of up to 12 measurements. The script also prints a bare number with no uncertainty, so
users cannot tell a reliable estimate from a poor one.

## What Changes

- The lag is estimated from the cost minimum of each segment pair separately. Each pair orientation is combined by
  its median, so outlier pairs no longer move the result. The mean of the two orientation medians, which cancels the
  bias of oblique anomalies, is kept.
- The script reports an uncertainty (± measurements) derived from the spread of the pair minima that agree with the
  estimate.
- The reliability check counts pairs that agree with the estimate, instead of applying the empirical
  `MIN_CONTRAST / sqrt(n)` threshold to the summed cost.
- The script prints diagnostics: pairs used, the estimate of each orientation, and how many pairs agree with the
  result.
- Local tests on synthetic surveys with a known lag verify the change. They are not added to the repository.

## Non-goals

- No change to the output column (`<column>_LAG`) or to how the shift is applied per file line.
- No change to segment detection, pairing, binning or the gradient correlation cost of a single pair.
- No grid-based joint estimation, sub-sample refinement or use of the time column.
- No Java estimator or Auto button in `TimeLagTool`.
- No change to the GNSS lag estimation in the JeffRoth fork; it is not adopted.

## Capabilities

### New Capabilities
- `auto-time-lag`: estimating the sensor time lag of a data file from reciprocal survey lines and writing the
  lag-corrected series (bundled Auto Time Lag Correction script).

### Modified Capabilities
<!-- none -->

## Impact

- `scripts/auto_time_lag_correction.py`: estimate aggregation, reliability check and console output.
- Local pytest file `manual-tests/1087/test_auto_time_lag_correction.py`. The folder is excluded from git locally, so
  the tests are not committed.
- No new runtime dependencies.
