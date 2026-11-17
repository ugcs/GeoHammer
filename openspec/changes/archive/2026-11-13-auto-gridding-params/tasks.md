## 1. Building blocks

- [x] 1.1 Add `PrincipalComponents.findOffsetComponents(double[] dx, double[] dy)`: uncentred second-moment matrix
  of the vectors weighted by their length, with a closed-form 2x2 eigen decomposition, returning `Components2`. Add
  `PrincipalComponentsTest` for its direction, sign invariance, weighting and ratio. Verify with
  `mvn -Dtest=PrincipalComponentsTest test`.
- [x] 1.2 Add `KdTree.nearestNeighbor(Point2D, Predicate<V>)` that tightens the search bound only on accepted
  candidates. Add tests comparing it with a brute-force filtered search on random points. Verify with
  `mvn -Dtest=KdTreeTest test`.
- [x] 1.3 Create `service/survey/SurveyService` with the local-meter `Track` projection (rows without positions
  skipped) and `OptionalDouble estimateLineSpacing(SgyFile)`, plus a package-private overload choosing the estimator.
  Verify with `mvn compile`.

## 2. Estimators

- [x] 2.1 Add a test track generator (serpentine lines with given spacing, heading, sampling, turns on/off, tie lines,
  gaps, line order, re-flown line, two blocks, position noise, curved lines, spiral, single line). Verify that a
  generator self-test checks the point counts and the spacing of the generated lines.
- [x] 2.2 Implement `LineReconstructionSpacing` (PCA direction, cut at u reversals with hysteresis, straight stretches,
  overlap-aware gaps, median). Verify with its cases in `SurveyServiceTest` for the survey-geometry scenarios.
- [x] 2.3 Implement `ParallelPassSpacing` (resampling, vertex directions, filtered nearest parallel pass, median
  perpendicular distance). Verify with its cases in `SurveyServiceTest` for the survey-geometry scenarios.
- [x] 2.4 Make `SurveyServiceTest` run every synthetic case for both estimators, including curved lines and a spiral,
  and print a side-by-side table of relative errors. Verify with `mvn -Dtest=SurveyServiceTest test`.

## 3. Comparison

- [x] 3.1 Add `SurveyServicePerformanceTest` (`@Tag("performance")`) measuring time and allocated bytes
  (`ThreadMXBean.getThreadAllocatedBytes`) of both estimators on 100k and 1M point tracks after warm-up. Verify with
  `mvn test -Dgroups=performance -DexcludedGroups= -Dtest=SurveyServicePerformanceTest`.
- [x] 3.2 Add a real data test (`@Tag("performance")`) reading files from `-Dsurvey.data.dir` with planned spacings
  from `spacing.properties`, opened as `CsvFile` with the app templates; skipped when the property is not set. Verify
  it reports error, time and allocation per file and estimator when run on the user's folder.
- [x] 3.3 Run the comparison, tune the named thresholds, and report the results to the user; the user picks the
  estimator. Verify with the user's choice recorded in `design.md`.
- [x] 3.4 Delete the losing estimator, the estimator switch in `SurveyService`, and `KdTree.nearestNeighbor` with its
  tests if no longer used; keep the winner's synthetic tests as its unit tests. Simplify the kept estimator and make
  the spacing of a line the mean of the two gaps above it. Verify with `mvn test` and no
  references to the deleted classes.

## 4. Gridding parameters

- [x] 4.1 Move the input limits to `GriddingParams` constants and add `GriddingParams.estimate(double lineSpacing)`
  (spacing / 4, spacing x 2, rounded half up to 2 decimals, clamped to [0.01, 100]). Add tests for the ratios,
  rounding and clamping. Verify with `mvn -Dtest=GriddingParamsTest test`.
- [x] 4.2 Add `GriddingSettings.findParams(SgyFile)` returning saved parameters or null, with a test on a real
  `Settings`. Verify with `mvn -Dtest=GriddingSettingsTest test`.

## 5. Gridding tool

- [x] 5.1 Add the `.button.flat` rule to `components.css` and the flat "Auto" button at the end of the inputs row,
  bottom-aligned. Verify with `mvn compile`.
- [x] 5.2 Run estimates on a `SinglePendingExecutor`, apply results on the FX thread only for the still selected file;
  on Auto fill the inputs, on no estimate fill 0.1 / 1.0 and log it without a dialog. Verify with `mvn compile`.
- [x] 5.3 In `loadPreferences()`, when the file has no grid and the template no saved parameters, clear the inputs,
  estimate, then fill and save the estimate, or fill 0.1 / 1.0 without saving. Verify with `mvn compile`.
- [x] 5.4 Make `applyFilter()` save only the filter, keeping parameter saving on Apply / Apply to all. Verify with
  `mvn compile`.

## 6. MCP

- [x] 6.1 Add `EstimateGriddingParams` (`estimate_gridding_params`, read-only, optional `file`) returning
  `cell_size`, `blanking_distance` and `line_spacing`, error when no lines are detected; register it in `McpTools`.
  Verify with `mvn compile`.

## 7. Specs

- [x] 7.1 Update `spec/overview.md`: add survey geometry analysis to the `service` package line. Verify by reading the
  updated section.

## 8. Verify

- [x] 8.1 Run `mvn clean package` and fix new compilation errors and Error Prone/NullAway warnings.
- [x] 8.2 Invoke the `code-review` skill on the uncommitted changes with the proposal and specs as the completeness
  reference, fix critical and major issues, and report the scores and any issues left open.

## 9. Refinements

- [x] 9.1 Rework the estimator: `findOffsetComponents` over the points of the track, rotation in place,
  `StretchStatistics` inside `LineSpacing`, spacing from the neighbors above a line only, two lines as the minimum.
  Verified on the sample surveys.
- [x] 9.2 Replace `GriddingParams.estimate` with `fromLineSpacing`: cell = 0.2 x spacing, blanking = 1 x spacing,
  rounded with `Numbers.round` and clamped to [0.01, 100]; update the MCP tool description and return the line
  spacing without rounding. Verified with `mvn compile`.
- [x] 9.3 Add the warning banner to `FilterToolView` (`showWarning`, `hideWarning`, `.warning-banner`) and show both
  gridding warnings in it; "Auto" without an estimate keeps the inputs and shows "No survey lines detected".
  Verified in the app by the user.
- [x] 9.4 Do not save 0.1 / 1.0 for the template when the estimate of the selected file fails. Verified by reading
  `GriddingTool.submitAutoParams`: parameters are saved only for an estimate.
- [x] 9.5 Remove the comparison tests (`SurveyServiceTest`, `TrackGenerator`, `TrackGeneratorTest`, `Track`,
  `SurveyServicePerformanceTest`, `GriddingParamsTest`, `GriddingSettingsTest`) and bring `PrincipalComponentsTest`
  in line with `findOffsetComponents`. Verified with `mvn test`.
- [x] 9.6 Show the recalculation warning in `GriddingTool.onChange` only when the selected file has a grid, so that
  the banner does not blink when a file is opened. Verify by opening a csv file with the gridding tool expanded.
- [x] 9.7 Return no estimate instead of throwing for a file whose rows all lack positions: `LineSpacing.estimate`
  gets empty arrays and `detectStretches` still returns the range `[0, 1)`. Verify with `mvn compile` and an
  `estimate_gridding_params` call for such a file.
- [x] 9.8 Clamp the blanking distance in `GriddingParams.fromLineSpacing` with `MAX_BLANKING_DISTANCE` instead of
  `MAX_CELL_SIZE`. Verify with `mvn compile`.
- [x] 9.9 Run `mvn clean package` and the `code-review` skill on the uncommitted changes again, and fix critical and
  major issues.
