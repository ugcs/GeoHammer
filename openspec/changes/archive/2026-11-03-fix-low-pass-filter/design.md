## Context

`SensorLineChart.applyFilter` runs on the shared executor (via `LowPassTool` / MCP `ApplyFilter`), splits the series by
line ranges and calls `LowPassFilter.apply` per line. Today:

```
LowPassFilter.apply(line of n)
  N = min(order, (n-1)/2)                       <-- clamp exists only for the broken tail padding
  N == 0 -> return line
  FirFilter(N).filterList(line)                 <-- streaming circular buffer, N taps
      head pad: d[N-1] .. d[0]                  (outputs dropped)
      body:     d[0] .. d[n-1]
      tail pad: d[n-N-1] .. d[n-2N]             <-- bug: should continue from d[n-1]
  subList(N/2, n + N/2)                         <-- undo delay N/2
  scale by rms(line) / rms(filtered)            <-- whole-line rescale
```

Kernel: sinc with normalized cutoff `2/N`, centred at `N/2`, times a Hamming window of `N` points centred at `(N-1)/2`.
For `N = 1` the window divides 0/0 (NaN). For even `N` the two centres differ by half a tap, so the kernel is
lopsided and the output is shifted by about 0.3 measurements. The sinc's first zeros are at `±N/2`, so every tap is
non-negative. Missing values (null, NaN) are skipped per output and the result is divided by the sum of the taps used
(added in #481); the filter therefore already has unit gain.

The RMS rescale dates from the first implementation (#82), when the taps were not normalized and the gain was not 1.
It has been redundant since #481 and now only distorts results.

`FirFilter` is used only by `LowPassFilter`.

## Goals / Non-Goals

**Goals:**
- One kernel rule for all lengths that is symmetric, delay-free after alignment, and defined for length 1.
- Padding that only needs the line itself, so the per-line length limit follows from the line length.

**Non-Goals:**
- Changing the cutoff (`2/N`), the Hamming window or the missing-value handling inside a line.
- Moving the filter out of `math/filter` or changing `SequenceFilter`, `applyFilter`, `LowPassTool` or `ApplyFilter`.

## Decisions

### Symmetric kernel of odd length
With `m = N / 2` (integer division) the kernel has `2m + 1` taps; the sinc (cutoff `2/N`) and a Hamming window of
`2m + 1` points are both centred at tap `m`. A window of one point is `[1.0]`.

```
N = 8   today: n = -4 .. +3, window centre 3.5   new: n = -4 .. +4, window centre 4
N = 9   today: n = -4 .. +4                      new: same kernel
N = 1   today: window 0/0 -> NaN                 new: 1 tap [1.0] -> identity
```

For odd `N` the kernel is identical to today's. For even `N` the added tap at `n = +m` and the existing one at `n = -m`
sit on the sinc's zeros, so the smoothing strength does not change; only the lopsidedness goes away.

Alternative: centre the sinc at `(N-1)/2` and keep `N` taps. Rejected: for even `N` the delay is a half measurement,
which an integer shift cannot undo.

### Direct convolution over a padded line
`FirFilter.filterList` builds the padded line (`m` mirrored samples before and after) and computes each output at the
position of its input:

```
out[k] = sum(c[j] * x[k - m + j]) / sum(c[j])     over taps j where x[k - m + j] is available
out[k] = null                                     when no tap has an available sample
```

The output has the size of the line and no delay, so `LowPassFilter` drops the `subList` shift. The circular buffer and
its state go away.

Alternative: keep the streaming buffer and fix the tail index. Rejected: the buffer, the full-length padding and the
external shift are three places that must agree on the delay; the bug came from exactly that. The class is small and has
one caller, so the rewrite stays local.

### Point-mirrored ends with an available pivot
Head: pivot `a` is the first available value of the line; padded sample at position `-i` (`i = 1..m`) is `2a - x[i]`.
Tail: pivot `b` is the last available value; padded sample at position `n-1+i` is `2b - x[n-1-i]`. A padded sample whose
source `x[...]` is missing is missing. A line with no available values yields all-null output.

Point reflection keeps a linear trend exact through the end (`2b - x[n-1-i]` continues the ramp), unlike plain
mirroring, which bends it back (ramp end 1198.4 instead of 1199 for `N = 8`).

Alternative: always pivot on `x[0]` / `x[n-1]`. Rejected: a missing end value would make the whole padding missing and
the end would fall back to a one-sided average, bending the trend.

### Length limit per line
`LowPassFilter` uses `N = min(order, n)` on a line of `n` measurements and returns the line unchanged when `N <= 1`.
The old `(n-1)/2` limit is dropped: it only protected the broken tail index. `FirFilter` itself accepts any line: it
returns empty and single-value lines as they are, and when `m` exceeds the line it keeps mirroring the padding from the
opposite end. The limit stays in `LowPassFilter` because a kernel much wider than the line turns the result into a
straight line between the two end values.

### Remove the RMS rescale
`LowPassFilter` returns the convolution result as is. Unit gain comes from dividing by the sum of the used taps.
`calculateRms` is deleted.

### Data, threads and events
No change. The filter still runs on the executor inside `applyFilter`, which writes the `_LPF` column, calls
`setUnsaved(true)` and publishes `FileUpdatedEvent` and `FilterAppliedEvent` as today. Series filters stay out of undo
(they add a new column). No caches depend on `_LPF` values beyond what `FileUpdatedEvent` already refreshes.

## Risks / Trade-offs

- [Re-applying the filter gives different values than older `_LPF` columns] → Intended; the old values are wrong at line
  ends and slightly shifted everywhere. Mentioned in the proposal as a result-level breaking change.
- [Point reflection gives the end value twice the influence, so noise on the last measurement weighs more] → Checked on
  a noisy line: end values differ from plain mirroring by at most 0.01; the trend gain outweighs it.
- [Short lines are smoothed more when the entered length exceeds half the line] → Follows the entered length; the limit
  only prevents the kernel from exceeding the line.
- [Pivot at the first/last available value while mirroring around the line end position] → When end values are
  missing, the mirror point is slightly off position; the result stays numeric and close to the trend, which is what
  the spec requires.
