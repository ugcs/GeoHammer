## 1. FIR kernel and filtering

- [x] 1.1 In `FirFilter`, build the kernel with half-width `m = order / 2` and `2m + 1` taps: sinc with cutoff
  `2 / order` and a Hamming window of `2m + 1` points, both centred at tap `m`; a one-point window is `[1.0]`; verify by
  compiling
- [x] 1.2 Replace the circular buffer in `FirFilter.filterList` with a direct convolution over the line padded by `m`
  point-mirrored samples per end (pivot on the first/last available value; a padded sample with a missing source is
  missing); each output is the tap-weighted average of the available samples, aligned with its input, or null when
  none is available; the result has the size of the input; verify by compiling

## 2. Low-pass filter

- [x] 2.1 In `LowPassFilter.apply`, use `min(order, line size)` as the length, return the line unchanged when it is 0
  or 1, drop the `subList` shift and the RMS rescale (delete `calculateRms`); verify by compiling
- [x] 2.2 Add `LowPassFilterTest` (package `math.filter`) covering the spec scenarios: length 1 and a 3-point line
  return the input; a constant line stays constant; spikes with lengths 4 and 5 stay centred, symmetric and keep a sum
  of 10; ramps 1190..1199 with lengths 4 and 8 are returned unchanged (within 1e-9); a 40-point line with a missing
  last value and an all-missing line give no NaN (the latter all null); verify it passes

## 3. Verify

- [x] 3.1 Run `mvn clean package`; fix new compilation errors and Error Prone/NullAway warnings, and verify all tests
  pass
- [x] 3.2 Invoke the `code-review` skill on the uncommitted changes with this change's proposal and specs as the
  completeness reference; fix critical and major issues, and report the scores and any issues left open
