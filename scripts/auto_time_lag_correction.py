import sys
import argparse

import numpy as np
import pandas as pd

from script_utils import detect_separator, semantic_column, LATITUDE, LONGITUDE, LINE

LAG_SUFFIX = "_LAG"
EARTH_RADIUS = 6371000.0

# track length (m) over which the heading at a point is measured
HEADING_WINDOW = 5.0
# max deviation (degrees) of the heading from the survey axis for a straight segment
MAX_HEADING_DEVIATION = 20.0
# straight segments shorter than this fraction of the longest one are turns
# and obstacle detours
MIN_SEGMENT_FRACTION = 0.3
# neighbour segments farther apart than this many line spacings are not paired
MAX_PAIR_SPACING = 2.5
# values are averaged in bins of this size (m) along the axis, or of one
# measurement step if larger; keeps the cost independent of the sample rate
MIN_BIN_SIZE = 0.1
# boxcar smoothing length (m) before differentiation
SMOOTH_LENGTH = 0.5
# search range along track in meters: covers the lag plus the offset
# oblique anomalies add to single pairs
MAX_LAG_DISTANCE = 6.0
# minimum relative depth of the cost minimum to trust the estimate of a single
# pair; noise averages out over pairs, so n pairs need MIN_CONTRAST / sqrt(n)
# (noise only reaches ~0.35 / sqrt(n))
MIN_CONTRAST = 0.5


def resolve_column(data, name):
    if name in data.columns:
        return name
    target = name.strip().lower()
    for col in data.columns:
        if str(col).strip().lower() == target:
            return col
    return None


def require_column(data, name):
    col = resolve_column(data, name)
    if col is None:
        print(f"Error: Column '{name}' not found in input data")
        sys.exit(1)
    return col


def split_runs(ids):
    # contiguous runs of the same id as [start, end) ranges
    ranges = []
    start = 0
    for i in range(1, len(ids) + 1):
        if i == len(ids) or ids[i] != ids[start]:
            ranges.append((start, i))
            start = i
    return ranges


def to_local_meters(lat, lon):
    lat0 = np.nanmean(lat)
    lon0 = np.nanmean(lon)
    x = np.radians(lon - lon0) * EARTH_RADIUS * np.cos(np.radians(lat0))
    y = np.radians(lat - lat0) * EARTH_RADIUS
    return x, y


def fill_repeated_positions(x, y):
    # GPS may update slower than the sensor: interpolate repeated fixes by row index
    x = x.copy()
    y = y.copy()
    repeated = np.zeros(x.size, dtype=bool)
    repeated[1:] = (x[1:] == x[:-1]) & (y[1:] == y[:-1])
    x[repeated] = np.nan
    y[repeated] = np.nan
    rows = np.arange(x.size)
    ok = np.isfinite(x) & np.isfinite(y)
    if np.count_nonzero(ok) < 2:
        return x, y
    first, last = rows[ok][0], rows[ok][-1]
    inner = (rows >= first) & (rows <= last)
    x[inner] = np.interp(rows[inner], rows[ok], x[ok])
    y[inner] = np.interp(rows[inner], rows[ok], y[ok])
    return x, y


def source_rows(rows, shift, line_starts, line_ends):
    # as TimeLagFilter within each file line: result[j] = values[j + shift],
    # empty past the line ends
    source = rows + shift
    ok = (source >= line_starts) & (source < line_ends)
    return source, ok


def file_line_ranges(data):
    line_col = resolve_column(data, semantic_column(LINE))
    if line_col is None:
        return [(0, len(data))]
    return split_runs(data[line_col].to_numpy())


def line_bounds(line_ranges, size):
    # [start, end) of the file line each row belongs to
    starts = np.zeros(size, dtype=np.int64)
    ends = np.zeros(size, dtype=np.int64)
    for start, end in line_ranges:
        starts[start:end] = start
        ends[start:end] = end
    return starts, ends


class Segment:

    def __init__(self, rows, axis_pos, cross_pos, sign, line_starts, line_ends):
        # rows of the file; axis_pos is the position along the survey axis;
        # line_starts/line_ends bound the file line of each row
        self.rows = rows
        self.axis_pos = axis_pos
        self.cross_pos = cross_pos
        self.sign = sign
        self.line_starts = line_starts
        self.line_ends = line_ends

    def shifted_values(self, values, shift):
        # shifted values of the segment rows only, without shifting the whole file
        source, ok = source_rows(self.rows, shift, self.line_starts, self.line_ends)
        result = np.full(self.rows.size, np.nan)
        result[ok] = values[source[ok]]
        return result


class SegmentPair:

    def __init__(self, a, b, lo, bin_count, bin_size):
        self.a = a
        self.b = b
        self.bin_count = bin_count
        self.bins_a = np.floor((a.axis_pos - lo) / bin_size).astype(np.int64)
        self.bins_b = np.floor((b.axis_pos - lo) / bin_size).astype(np.int64)
        self.window = max(3, int(round(SMOOTH_LENGTH / bin_size)))

    def correlation_terms(self, values, shift):
        # mean gA * gB and sqrt(mean gA^2 * mean gB^2) of the gradient profiles;
        # their ratio is the correlation, insensitive to the amplitude difference
        # of an anomaly seen from lines at different distances
        ga = binned_gradient(self.bins_a, self.bin_count, self.a.shifted_values(values, shift), self.window)
        gb = binned_gradient(self.bins_b, self.bin_count, self.b.shifted_values(values, shift), self.window)
        ok = np.isfinite(ga) & np.isfinite(gb)
        if not np.any(ok):
            return 0.0, 0.0
        return np.mean(ga[ok] * gb[ok]), np.sqrt(np.mean(ga[ok] ** 2) * np.mean(gb[ok] ** 2))


def headings(px, py, dist):
    # direction over +/- half a window of track around each point
    half = HEADING_WINDOW / 2
    lo = np.clip(np.searchsorted(dist, dist - half, side="left"), 0, dist.size - 1)
    hi = np.clip(np.searchsorted(dist, dist + half, side="right") - 1, 0, dist.size - 1)
    dx = px[hi] - px[lo]
    dy = py[hi] - py[lo]
    return dx, dy, np.hypot(dx, dy)


def survey_axis(dx, dy, length, step_length):
    # dominant heading modulo 180 degrees, weighted by the distance travelled
    moving = length > 0
    angles = np.degrees(np.arctan2(dy[moving], dx[moving])) % 180.0
    hist, _ = np.histogram(angles, bins=180, range=(0.0, 180.0), weights=step_length[moving])
    # circular smoothing over 5 degrees
    padded = np.concatenate([hist[-2:], hist, hist[:2]])
    smoothed = np.convolve(padded, np.ones(5), mode="valid")
    angle = np.radians(np.argmax(smoothed) + 0.5)
    return np.array([np.cos(angle), np.sin(angle)])


def detect_segments(x, y, line_starts, line_ends):
    # straight parts of the track along the dominant survey direction
    valid_rows = np.flatnonzero(np.isfinite(x) & np.isfinite(y))
    if valid_rows.size < 2:
        return []
    px, py = x[valid_rows], y[valid_rows]
    step_length = np.concatenate([[0.0], np.hypot(np.diff(px), np.diff(py))])
    dist = np.cumsum(step_length)

    dx, dy, length = headings(px, py, dist)
    if not np.any(length > 0):
        return []
    axis = survey_axis(dx, dy, length, step_length)
    normal = np.array([-axis[1], axis[0]])

    with np.errstate(invalid="ignore", divide="ignore"):
        cos = (dx * axis[0] + dy * axis[1]) / length
    min_cos = np.cos(np.radians(MAX_HEADING_DEVIATION))
    direction = np.zeros(valid_rows.size, dtype=int)
    direction[cos >= min_cos] = 1
    direction[cos <= -min_cos] = -1

    runs = []
    for start, end in split_runs(direction):
        if direction[start] != 0 and end - start >= 2:
            runs.append((start, end, dist[end - 1] - dist[start]))
    if not runs:
        return []
    min_length = MIN_SEGMENT_FRACTION * max(run_length for _, _, run_length in runs)

    segments = []
    for start, end, run_length in runs:
        if run_length < min_length:
            continue
        sx, sy = px[start:end], py[start:end]
        rows = valid_rows[start:end]
        segments.append(Segment(
            rows,
            sx * axis[0] + sy * axis[1],
            float(np.median(sx * normal[0] + sy * normal[1])),
            int(direction[start]),
            line_starts[rows],
            line_ends[rows]))
    segments.sort(key=lambda segment: segment.cross_pos)
    return segments


def sample_step(segments):
    # distance per row, robust to GPS updating slower than the sensor
    steps = []
    for segment in segments:
        rows = segment.rows[-1] - segment.rows[0]
        if rows > 0:
            steps.append(abs(segment.axis_pos[-1] - segment.axis_pos[0]) / rows)
    return float(np.median(steps)) if steps else 0.0


def find_pairs(segments, bin_size):
    # adjacent segments flown in opposite directions and overlapping along the axis
    candidates = []
    for a, b in zip(segments, segments[1:]):
        if a.sign != b.sign:
            candidates.append((a, b, b.cross_pos - a.cross_pos))
    if not candidates:
        return []
    spacing = float(np.median([gap for _, _, gap in candidates]))
    pairs = []
    for a, b, gap in candidates:
        if gap > MAX_PAIR_SPACING * spacing:
            continue
        lo = max(np.min(a.axis_pos), np.min(b.axis_pos))
        hi = min(np.max(a.axis_pos), np.max(b.axis_pos))
        if hi <= lo:
            continue
        pairs.append(SegmentPair(a, b, lo, int((hi - lo) / bin_size), bin_size))
    return pairs


def binned_gradient(bins, bin_count, vals, window):
    # bin means along the axis, differentiated to drop the line level
    ok = np.isfinite(vals) & (bins >= 0) & (bins < bin_count)
    counts = np.bincount(bins[ok], minlength=bin_count)
    sums = np.bincount(bins[ok], weights=vals[ok], minlength=bin_count)
    filled = counts > 0
    if np.count_nonzero(filled) < 2:
        return np.full(bin_count, np.nan)
    index = np.arange(bin_count)
    means = np.interp(index, index[filled], sums[filled] / counts[filled], left=np.nan, right=np.nan)
    smoothed = np.convolve(means, np.ones(window) / window, mode="same")
    # zero padding of the convolution distorts the ends
    edge = window // 2 + 1
    smoothed[:edge] = np.nan
    smoothed[-edge:] = np.nan
    return np.gradient(smoothed)


def total_cost(pairs, values, shift):
    # C(k) = 1 - sum covariance / sum norm: 0 is a perfect match, ~1 is unrelated;
    # pairs with strong anomalies weigh more than noise
    covariance = 0.0
    norm = 0.0
    for pair in pairs:
        pair_covariance, pair_norm = pair.correlation_terms(values, shift)
        covariance += pair_covariance
        norm += pair_norm
    return 1.0 - covariance / norm if norm > 0 else np.nan


def search_shift(pairs, values, max_shift, coarse_step):
    # coarse pass over the whole range, then every shift around the coarse minimum
    coarse = np.arange(-max_shift, max_shift + 1, coarse_step)
    costs = np.array([total_cost(pairs, values, shift) for shift in coarse])
    if not np.any(np.isfinite(costs)):
        return None
    coarse_best = int(coarse[np.nanargmin(costs)])
    median = float(np.nanmedian(costs))

    fine = np.arange(max(-max_shift, coarse_best - coarse_step), min(max_shift, coarse_best + coarse_step) + 1)
    fine_costs = np.array([total_cost(pairs, values, shift) for shift in fine])
    best_index = int(np.nanargmin(fine_costs))
    best = int(fine[best_index])
    contrast = (median - fine_costs[best_index]) / median if median > 0 else 0.0
    if contrast < MIN_CONTRAST / np.sqrt(len(pairs)) or abs(best) >= max_shift:
        return None
    return best


def estimate_shift(pairs, values, max_shift, coarse_step):
    # Oblique anomalies bias the minimum by +b for one pair orientation and by -b
    # for the other, so the mean of per-orientation minimums cancels it.
    forward = search_shift([pair for pair in pairs if pair.a.sign > 0], values, max_shift, coarse_step)
    backward = search_shift([pair for pair in pairs if pair.a.sign < 0], values, max_shift, coarse_step)
    if forward is not None and backward is not None:
        return int(round((forward + backward) / 2)), None
    if forward is not None or backward is not None:
        shift = forward if forward is not None else backward
        return shift, "only one pair orientation is reliable, oblique anomalies may bias the result"
    return None, None


def main():
    parser = argparse.ArgumentParser(
        description="Estimates the sensor time lag from adjacent straight track segments flown in"
                    " opposite directions and writes the shifted series to <column>_LAG."
    )
    parser.add_argument("file_path", help="File path")
    parser.add_argument("--column", default="TMI", help="Series to estimate the lag on (default: TMI)")
    args = parser.parse_args()

    input_path = args.file_path
    output_path = args.file_path

    separator = detect_separator(input_path)
    # read as text so the file is written back without reformatting any value
    data = pd.read_csv(input_path, sep=separator, dtype=str, keep_default_na=False)

    value_col = require_column(data, args.column)
    values = pd.to_numeric(data[value_col], errors="coerce").to_numpy(dtype=float)
    lat = pd.to_numeric(data[require_column(data, semantic_column(LATITUDE))], errors="coerce").to_numpy(dtype=float)
    lon = pd.to_numeric(data[require_column(data, semantic_column(LONGITUDE))], errors="coerce").to_numpy(dtype=float)
    x, y = fill_repeated_positions(*to_local_meters(lat, lon))

    line_ranges = file_line_ranges(data)
    line_starts, line_ends = line_bounds(line_ranges, len(data))
    segments = detect_segments(x, y, line_starts, line_ends)

    step = sample_step(segments)
    if step <= 0:
        print("Error: Time lag cannot be determined: no straight segments in the track")
        sys.exit(1)

    bin_size = max(step, MIN_BIN_SIZE)
    pairs = find_pairs(segments, bin_size)
    if not pairs:
        print("Error: Time lag cannot be determined: no adjacent segments flown in opposite directions")
        sys.exit(1)

    max_shift = int(np.ceil(MAX_LAG_DISTANCE / step))
    coarse_step = max(1, int(round(bin_size / step)))
    shift, warning = estimate_shift(pairs, values, max_shift, coarse_step)
    if shift is None:
        print("Error: Time lag cannot be determined reliably: no pair has a distinct cost minimum."
              " Check that the survey has anomalies")
        sys.exit(1)
    if warning is not None:
        print(f"Warning: {warning}")
    print(f"Estimated shift: {shift} measurements (~{shift * step:.2f} m along track)")

    base = value_col[:-len(LAG_SUFFIX)] if value_col.endswith(LAG_SUFFIX) else value_col
    target = base + LAG_SUFFIX
    text = data[value_col].to_numpy(dtype=object)
    source, ok = source_rows(np.arange(len(data)), shift, line_starts, line_ends)
    # target column type is string with empty values for n/a
    result = np.full(len(data), "", dtype=object)
    result[ok] = text[source[ok]]
    data[target] = result
    data.to_csv(output_path, index=False, sep=separator)


if __name__ == "__main__":
    main()
