# Tasks

## 1. Local test harness (not committed)

- [x] 1.1 Create `manual-tests/1087/test_auto_time_lag_correction.py` with a synthetic survey builder: serpentine of
  parallel lines with turns, constant speed, induced dipoles (inclination 68°, declination 5°), a heading offset per
  direction, noise and a known lag. It imports `scripts/auto_time_lag_correction.py` via `sys.path`. Verify with
  `git status`: the file is not listed, because `manual-tests/` is excluded locally.
- [x] 1.2 Add one test per scenario of `specs/auto-time-lag/spec.md`: scattered dipoles, one dominant anomaly, outlier
  pairs, oblique linear anomaly, noise only, two lines (warning), the `_LAG` column written, and the file unchanged on
  rejection. Add an optional real-file test, skipped unless `AUTO_TIME_LAG_FILE` is set. Run
  `python3 -m pytest manual-tests/1087` against the current script and note which tests fail as the baseline.

## 2. Robust estimate

- [x] 2.1 In `scripts/auto_time_lag_correction.py`, find the minimum of each pair on its own. Use the existing
  coarse-then-fine search on a single pair, with contrast ≥ `MIN_CONTRAST` and edge minima dropped. Replace the
  summed-cost search and remove code that becomes unused. Verify that the scattered-dipole test still passes.
- [x] 2.2 Combine the pair minima as the median per orientation and the rounded mean of the two medians, keeping the
  single-orientation warning. Verify that the dominant-anomaly, outlier-pair, oblique-anomaly and two-line tests pass.
- [x] 2.3 Replace the `MIN_CONTRAST / sqrt(n)` check with the agreement check: tolerance 0.5 m along track as a named
  constant, at least 2 agreeing pairs and at least one per orientation that has pairs. Verify that the noise-only and
  unchanged-file tests pass.

## 3. Uncertainty and diagnostics

- [x] 3.1 Compute the uncertainty as 1.4826 · MAD of the agreeing pair minima / sqrt(n), at least 1 row. Print the
  pairs used, `k_fwd`, `k_back`, the agreeing pairs out of the pairs used, and the shift with ± in rows and meters.
  Verify with a test that asserts these lines in the captured stdout.
- [x] 3.2 Run the script on the Brandenburg QuSpin file (`AUTO_TIME_LAG_FILE=<copy of the file>` with the optional
  test). Verify that an estimate between −26 and −15 rows is reported and that `k_fwd` and `k_back` both fall in that
  range.

## 4. Verify

- [x] 4.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings.
- [x] 4.2 Invoke the `code-review` skill on the uncommitted changes, with this change's proposal and specs as the
  completeness reference. Fix critical and major issues, and report the scores and any issues left open.
