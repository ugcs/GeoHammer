# Design

## Context

`scripts/auto_time_lag_correction.py` runs as an external Python process through the existing script execution. It
reads the CSV, writes `<column>_LAG` and exits; the app reloads the file as for any script. This change has no Java,
FX thread, event, undo or cache impact: only the estimation inside the script changes.

Current flow: `detect_segments` → `find_pairs` → `estimate_shift`. `estimate_shift` calls `search_shift` once per pair
orientation ("forward below" and "backward below"). Each call minimizes `total_cost`, which is
1 − Σcovariance / Σnorm over all pairs of that orientation. The result is rejected when the contrast of the summed
minimum is below `MIN_CONTRAST / sqrt(n)`, and the final shift is the mean of the two orientation minima.

Findings that shape this design (see proposal.md, Why):
- On the Brandenburg QuSpin file (0.4 cm/row, ±1519 rows searched), 5 pairs agree on −15…−26 rows. The other 6 pairs
  have minima at 0.57–3.5 m with contrast up to 0.81. With the summed cost, the backward orientation lands on −43.
- On semi-synthetic data (real track, AGL and noise; induced dipoles; known lag), the median of the per-pair minima
  had an RMSE of 1.9–4.1 rows in all scenarios. The current summed cost had 2.2–6.5 rows, and the minimum of the median
  cost had 12–13 rows.

## Goals / Non-Goals

**Goals:**
- Make the result independent of single pairs while keeping the cancellation of oblique-anomaly bias.
- Replace the empirical summed-contrast threshold with an agreement-based check and report the uncertainty.

**Non-Goals:**
- Tuning segment detection, binning, smoothing or the search range (±6 m stays).
- Committed tests or test infrastructure for Python scripts.

## Decisions

**1. Each pair gets its own minimum.** The existing coarse-then-fine search runs on a single pair. Its contrast
threshold `MIN_CONTRAST / sqrt(1)` = 0.5 is kept per pair, as is the rejection of minima on the window edge. Pairs that
fail either check are dropped. The cost of one pair (`correlation_terms`) is unchanged. The total work is the same as
today, because `total_cost` already evaluates every pair at every shift.
*Alternatives:* the current summed cost (one strong pair decides the result) and the minimum of the median cost (RMSE
12–13 rows in the semi-synthetic test). Both were rejected.

**2. Median per orientation, then the mean of the two medians.** `k_fwd = median(k_i | forward below)`,
`k_back = median(k_i | backward below)`, result = `round((k_fwd + k_back) / 2)`. This keeps the mean of the two
orientations, which cancels the oblique bias (+s / −s), and replaces each orientation's summed cost with a robust
statistic.
*Alternative:* one median over all pairs. It tested well (RMSE ≤ 4.1), but it no longer cancels the bias when the
orientations have unequal pair counts. The local test with an oblique linear anomaly checks the chosen variant.

**3. Agreement tolerance: 0.5 m along track (`SMOOTH_LENGTH`), converted to rows with the sample step.** The pair cost
cannot resolve offsets finer than its smoothing. On the real file the consistent pairs lie within 4.4 cm of the result
and the nearest outlier lies 0.57 m away, so 0.5 m separates them.

**4. Reliability: at least 2 agreeing pairs, and at least one per orientation that has pairs.** This replaces the
`MIN_CONTRAST / sqrt(n)` check on the summed cost. When only one pair has a distinct minimum (e.g. a two-line survey),
that pair is enough, as before: it already passed the single-pair contrast threshold. Noise is rejected by two filters: the per-pair contrast (noise
reaches about 0.35) and the requirement that pairs agree. Random minima over ±6 m rarely fall within ±0.5 m of each
other in both orientations.

**5. Uncertainty: 1.4826 · MAD of the agreeing pair minima / sqrt(n_agree), at least 1 row.** It is printed in rows and
in meters (× step). A scaled MAD is consistent with the median used for the estimate; a bootstrap would add runtime
for little gain.

**6. Diagnostics in stdout.** The script prints the pairs used of the detected pairs, `k_fwd` and `k_back`, the
number of agreeing pairs out of the pairs used, and the shift with its ± uncertainty in rows and meters. The script
panel already shows stdout, so no format beyond plain lines is needed.

**7. Local tests: `manual-tests/1087/test_auto_time_lag_correction.py` (pytest), not committed.** The tests build a
synthetic serpentine track in code: parallel lines, constant speed, turns between lines. They add induced-dipole
anomalies, a heading offset per direction and noise, apply a known lag, and import the script module through
`sys.path`. One scenario per spec scenario. An optional test runs on a real file given through an environment
variable and is skipped otherwise.

## Risks / Trade-offs

- [The agreement tolerance and the minimum pair count were derived from one real file] → The local noise test checks
  rejection, and the outlier-pair test checks acceptance. If another real file is rejected, the user can check the
  printed diagnostics. The tolerance is one named constant.
- [Small surveys (2–3 lines) have one pair per orientation, so the median equals that pair] → This is no worse than
  today. The printed pair count shows it.
- [An orientation where most pairs are outliers gives a wrong median] → The agreement check then fails for that
  orientation and the script reports no reliable estimate instead of a wrong one.
- [The per-pair contrast threshold of 0.5 may drop weak but valid pairs on low-anomaly surveys] → This is the same
  threshold as today's single-pair case. Rejecting the survey is preferred to guessing.
