import sys
import os
import argparse
import math
import numpy as np
import pandas as pd
from script_utils import normalize_input_stem

try:
    import ppigrf
except ImportError:
    ppigrf = None

# Internal constants (not exposed as CLI args)
MARK_COLUMN = "Mark"
LINE_GAP_SECONDS = 30
TREND_DEGREE = 1
MIN_TREND_SAMPLES = 10
# Window padding in metres around a marked group
WINDOW_PADDING_M = 20.0
# Minimum unique spatial positions in a window for AS computation
MIN_WINDOW_SAMPLES = 20
K_FACTOR = 0.7111  # halfwidth → sensor-to-source distance; derived analytically for vertical dipole
CB_FACTOR = 1.910  # |B_max|/AS_max → sensor-to-source distance; derived analytically for vertical dipole
MAX_PEAK_OFFSET_M = 2.0
# Radius around the mark centre used to find the local AS peak for Method A.
# Prevents a stronger neighbouring anomaly from being selected as the AS peak.
AS_PEAK_SEARCH_RADIUS_M = 5.0
# Radius around the mark used to detect a dipole-shaped anomaly (sign change in B).
# Dipole detection prevents Method A from being preferred over the mean when the
# anomaly naturally has a positive + negative lobe (which narrows the A halfwidth).
DIPOLE_CHECK_RADIUS_M = 15.0
# Relative distance difference above which the two methods are considered to disagree.
METHOD_DISAGREE_THRESHOLD = 0.35
MIN_HALFWIDTH_M = 0.5
MAX_HALFWIDTH_M = 50.0
# Bin width for spatial aggregation (metres).
# Points closer than this are treated as the same position.
SPATIAL_BIN_M = 0.5
CSV_SEPARATOR = ","


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def build_along_track(lats, lons):
    """
    Cumulative along-track distance (metres). Fully vectorised haversine.
    """
    R = 6_371_000.0
    lat = np.radians(lats)
    lon = np.radians(lons)
    dlat = np.diff(lat)
    dlon = np.diff(lon)
    a = np.sin(dlat / 2) ** 2 + np.cos(lat[:-1]) * np.cos(lat[1:]) * np.sin(dlon / 2) ** 2
    seg = R * 2 * np.arcsin(np.sqrt(np.clip(a, 0.0, 1.0)))
    return np.concatenate([[0.0], np.cumsum(seg)])


def compute_igrf(lons, lats, alts_m, timestamps):
    """
    IGRF total intensity per row.
    Groups by unique date to avoid per-row model calls.
    """
    if ppigrf is None:
        print("Warning: ppigrf not installed — IGRF skipped, TMI used as anomaly.")
        return np.zeros(len(lats))

    ts = pd.to_datetime(pd.Series(timestamps), errors="coerce")
    date_series = ts.dt.date
    result = np.zeros(len(lats))

    for d in date_series.dropna().unique():
        mask = (date_series == d).values
        if not mask.any():
            continue
        mean_lat = float(np.nanmean(lats[mask]))
        mean_lon = float(np.nanmean(lons[mask]))
        mean_alt_km = float(np.nanmean(alts_m[mask])) / 1000.0
        try:
            Be, Bn, Bu = ppigrf.igrf(mean_lon, mean_lat, mean_alt_km, d)
            result[mask] = float(np.sqrt(Be ** 2 + Bn ** 2 + Bu ** 2))
        except Exception:
            pass
    return result


def split_into_lines(ts_series, gap_seconds=LINE_GAP_SECONDS):
    """
    Vectorised flight-line splitter.
    Returns list of numpy index arrays.
    """
    ts_ns = pd.to_datetime(ts_series, errors="coerce").values.astype(np.int64)
    nat = np.iinfo(np.int64).min
    diffs = np.diff(ts_ns)
    gap_ns = int(gap_seconds * 1e9)
    is_break = (diffs > gap_ns) & (ts_ns[:-1] != nat) & (ts_ns[1:] != nat)
    starts = np.concatenate([[0], np.where(is_break)[0] + 1])
    ends = np.concatenate([np.where(is_break)[0] + 1, [len(ts_ns)]])
    return [np.arange(s, e) for s, e in zip(starts, ends)]


def aggregate_spatial(x, tmi, marks, agl, lat, lon, ts, bin_m=SPATIAL_BIN_M):
    """
    Aggregate rows that fall in the same spatial bin into one representative row.
    Within each bin: mean TMI/AGL/lat/lon, OR of mark, first timestamp.

    Returns aggregated arrays with length = number of unique bins.
    The returned arrays are already sorted by x (ascending).
    """
    if len(x) == 0:
        return x, tmi, marks, agl, lat, lon, ts

    bins = np.floor(x / bin_m).astype(np.int64)
    unique_bins = np.unique(bins)

    out_x = np.empty(len(unique_bins))
    out_tmi = np.empty(len(unique_bins))
    out_marks = np.zeros(len(unique_bins), dtype=np.float64)
    out_agl = np.full(len(unique_bins), np.nan)
    out_lat = np.empty(len(unique_bins))
    out_lon = np.empty(len(unique_bins))
    out_ts = np.empty(len(unique_bins), dtype=ts.dtype)

    for i, b in enumerate(unique_bins):
        mask = bins == b
        out_x[i] = x[mask].mean()
        out_tmi[i] = tmi[mask].mean()
        out_marks[i] = 1.0 if marks[mask].any() else 0.0
        agl_vals = agl[mask]
        valid_agl = agl_vals[~np.isnan(agl_vals)]
        if len(valid_agl):
            out_agl[i] = valid_agl.mean()
        out_lat[i] = lat[mask].mean()
        out_lon[i] = lon[mask].mean()
        out_ts[i] = ts[mask][0]

    return out_x, out_tmi, out_marks, out_agl, out_lat, out_lon, out_ts


def remove_trend(tmi_anom, x, marks, degree=TREND_DEGREE, min_samples=MIN_TREND_SAMPLES):
    """Remove polynomial trend using unmarked rows."""
    unmarked = marks == 0
    if unmarked.sum() < min_samples:
        return tmi_anom.copy(), True
    coeffs = np.polyfit(x[unmarked], tmi_anom[unmarked], degree)
    return tmi_anom - np.polyval(coeffs, x), False


def interp_crossing(x_arr, y_arr, threshold):
    """
    Vectorised: find x positions where y_arr crosses threshold.
    """
    y = y_arr - threshold
    signs = np.sign(y)
    cross = np.where((signs[:-1] * signs[1:] <= 0) & (y_arr[:-1] != y_arr[1:]))[0]
    if len(cross) == 0:
        return []
    t = (threshold - y_arr[cross]) / (y_arr[cross + 1] - y_arr[cross])
    return list(x_arr[cross] + t * (x_arr[cross + 1] - x_arr[cross]))


def find_local_halfwidth(x_arr, y_arr, peak_idx):
    """
    Find the halfwidth of a peak by searching LEFT then RIGHT from peak_idx
    for the first sample where y drops to y[peak_idx]/2.

    Unlike interp_crossing (which returns ALL crossings), this only uses the
    NEAREST crossing on each side, so neighbouring anomalies do not inflate
    the reported width.

    Returns (left_x, right_x); either value is None if no crossing is found.
    """
    threshold = y_arr[peak_idx] / 2.0
    n = len(x_arr)

    left_x = None
    for i in range(peak_idx - 1, -1, -1):
        if y_arr[i] <= threshold:
            if y_arr[i + 1] != y_arr[i]:
                t = (threshold - y_arr[i]) / (y_arr[i + 1] - y_arr[i])
                left_x = x_arr[i] + t * (x_arr[i + 1] - x_arr[i])
            else:
                left_x = x_arr[i]
            break

    right_x = None
    for i in range(peak_idx + 1, n):
        if y_arr[i] <= threshold:
            if y_arr[i] != y_arr[i - 1]:
                t = (threshold - y_arr[i - 1]) / (y_arr[i] - y_arr[i - 1])
                right_x = x_arr[i - 1] + t * (x_arr[i] - x_arr[i - 1])
            else:
                right_x = x_arr[i]
            break

    return left_x, right_x


def _dx_5point(b_arr, dx):
    """
    Horizontal derivative matching Java AnalyticSignalFilter.getXDerivative():
      - 5-point stencil for interior points:
            (-B[j+2] + 8·B[j+1] - 8·B[j-1] + B[j-2]) / (12·dx)
      - 3-point central difference one step from edges
      - forward / backward 1-point difference at the two edges
    """
    n = len(b_arr)
    d = np.empty(n)
    # 5-point stencil (indices 2 … n-3)
    if n >= 5:
        j = np.arange(2, n - 2)
        d[j] = (-b_arr[j + 2] + 8.0 * b_arr[j + 1]
                - 8.0 * b_arr[j - 1] + b_arr[j - 2]) / (12.0 * dx)
    # 3-point central (index 1 and n-2)
    for j in ([1] if n >= 3 else []):
        d[j] = (b_arr[j + 1] - b_arr[j - 1]) / (2.0 * dx)
    for j in ([n - 2] if n >= 3 and n - 2 != 1 else []):
        d[j] = (b_arr[j + 1] - b_arr[j - 1]) / (2.0 * dx)
    # forward / backward at the two boundary points
    if n >= 2:
        d[0]     = (b_arr[1]     - b_arr[0])     / dx
        d[n - 1] = (b_arr[n - 1] - b_arr[n - 2]) / dx
    else:
        d[0] = 0.0
    return d


def compute_as(x_arr, b_arr):
    """
    Analytic Signal amplitude for a 1-D profile — Roest et al. (1992).

        AS = sqrt( (dB/dx)^2 + (dB/dz)^2 )

    Matches Java AnalyticSignalFilter exactly:

    dB/dx — 5-point finite-difference stencil (_dx_5point),
            same as AnalyticSignalFilter.getXDerivative().

    dB/dz — vertical derivative in the wavenumber domain:
                dB/dz = Re( IFFT( |kx| · FFT(B) ) )
            where |kx| = 2π · |fftfreq(N, dx)| (centred ordering with
            negative frequencies), matching getZDerivativeMatrix():
                dkx = 2π / (N · dx),  kxIndex = j if j ≤ N/2 else j − N.

    This function is called ONCE on the full spatially-aggregated flight
    line (~1000 m / ~2000 bins), not on short per-mark windows, so the
    FFT periodicity assumption holds and edge artefacts are far from marks.

    K_FACTOR and CB_FACTOR are derived analytically from the vertical-dipole
    model on a fine grid — no empirical calibration against test data.

    Replaces NaN/Inf with 0.
    """
    N = len(b_arr)
    dx = float(np.mean(np.diff(x_arr))) if N > 1 else 1.0

    dBdx = _dx_5point(b_arr, dx)

    # Centred-frequency ordering: kxIndex = j if j <= N//2 else j - N
    # numpy fftfreq already returns this ordering multiplied by 1/N/dx.
    kx = np.fft.fftfreq(N, d=dx) * 2.0 * np.pi
    dBdz = np.real(np.fft.ifft(np.fft.fft(b_arr) * np.abs(kx)))

    AS = np.sqrt(dBdx ** 2 + dBdz ** 2)
    AS = np.where(np.isfinite(AS), AS, 0.0)
    return AS


def process_anomaly(window_x, window_b, AS, marked_mask_in_window,
                    mark_center_x=None):
    """
    Methods A (half-width) and B (AS ratio) for one anomaly window.
    AS is passed in — computed externally, not recalculated here.

    mark_center_x: along-track position of the mark centre (metres).
        Used to locate the local AS peak so that a stronger neighbouring
        anomaly in the same 40 m window does not contaminate the halfwidth.
    """
    flags = set()
    n = len(window_x)

    edge = max(1, int(n * 0.10))
    if n - 2 * edge < 1:
        flags.add("short_window")
        return dict(distance_A=None, distance_B=None, distance_mean=None,
                    halfwidth_m=None, b_max_nT=None, as_max_nT_m=None,
                    as_peak_idx=None, flags=flags)

    AS_valid = AS[edge:-edge]
    x_valid = window_x[edge:-edge]

    # Determine mark centre for local peak search
    marked_x_arr = window_x[marked_mask_in_window]
    if mark_center_x is None:
        mark_center_x = (float(np.mean(marked_x_arr)) if len(marked_x_arr) > 0
                         else float(np.median(window_x)))

    # Find the AS peak NEAREST to the mark centre (within search radius).
    # This prevents a stronger anomaly from a different nearby target from
    # being used as the reference peak for the halfwidth calculation.
    near_mask = ((x_valid >= mark_center_x - AS_PEAK_SEARCH_RADIUS_M) &
                 (x_valid <= mark_center_x + AS_PEAK_SEARCH_RADIUS_M))
    if near_mask.any():
        near_indices = np.where(near_mask)[0]
        as_peak_local = int(near_indices[int(np.argmax(AS_valid[near_mask]))])
    else:
        # Fallback: global maximum in the edge-trimmed window
        as_peak_local = int(np.argmax(AS_valid))

    AS_max = float(AS_valid[as_peak_local])
    as_peak_idx = edge + as_peak_local

    # --- Method A: Half-Width (search outward from local peak) ---
    # Using find_local_halfwidth ensures the nearest crossing on each side is
    # used, not the outermost crossings that may span to a neighbouring anomaly.
    left_x, right_x = find_local_halfwidth(x_valid, AS_valid, as_peak_local)
    halfwidth_m = distance_A = None

    if left_x is None or right_x is None:
        flags.add("partial_halfwidth")
    else:
        W = right_x - left_x
        halfwidth_m = W
        if W < MIN_HALFWIDTH_M:
            flags.add("narrow_anomaly")
        elif W > MAX_HALFWIDTH_M * 2:
            flags.add("wide_anomaly")
        distance_A = K_FACTOR * W

    # --- Method B: AS Ratio ---
    marked_b = window_b[marked_mask_in_window]
    marked_as = AS[marked_mask_in_window]
    b_max_nT = distance_B = None

    if len(marked_b) == 0:
        flags.add("peak_mismatch")
    else:
        b_max_idx = int(np.argmax(np.abs(marked_b)))
        b_max_nT = float(marked_b[b_max_idx])
        AS_at_peak = float(marked_as[b_max_idx])

        if AS_at_peak == 0:
            flags.add("peak_mismatch")
        else:
            distance_B = CB_FACTOR * abs(b_max_nT) / AS_at_peak

    # --- Dipole check ---
    # A dipole-type source produces both a positive and a significant negative
    # lobe in the detrended TMI near the mark.  When a dipole is detected we
    # keep the mean of A and B rather than preferring Method A, because
    # Method A's halfwidth only captures one lobe of the dipole and is
    # systematically too narrow.
    dipole_mask = ((window_x >= mark_center_x - DIPOLE_CHECK_RADIUS_M) &
                   (window_x <= mark_center_x + DIPOLE_CHECK_RADIUS_M))
    if dipole_mask.any():
        near_b_vals = window_b[dipole_mask]
        b_pos = float(np.max(near_b_vals))
        b_neg = float(np.min(near_b_vals))
        if b_pos > 0 and b_neg < -0.15 * b_pos:
            flags.add("dipole_anomaly")

    # --- Mean ---
    distance_mean = None
    if (distance_A is not None and distance_B is not None
            and "partial_halfwidth" not in flags and "peak_mismatch" not in flags):
        distance_mean = (distance_A + distance_B) / 2.0
        if distance_A > 0 and \
                abs(distance_A - distance_B) / max(distance_A, distance_B) > METHOD_DISAGREE_THRESHOLD:
            flags.add("method_disagreement")

    return dict(
        distance_A=distance_A,
        distance_B=distance_B,
        distance_mean=distance_mean,
        halfwidth_m=halfwidth_m,
        b_max_nT=b_max_nT if "peak_mismatch" not in flags else None,
        as_max_nT_m=AS_max,
        as_peak_idx=as_peak_idx,
        flags=flags,
    )


# ---------------------------------------------------------------------------
# Timestamp / altitude resolution
# ---------------------------------------------------------------------------

def resolve_timestamp(data):
    """Unified datetime series: Timestamp col → Date+Time cols → synthetic."""
    if "Timestamp" in data.columns:
        return pd.to_datetime(data["Timestamp"], errors="coerce")

    if "Date" in data.columns and "Time" in data.columns:
        combined = (data["Date"].astype(str).str.strip()
                    + " "
                    + data["Time"].astype(str).str.strip())
        parsed = pd.to_datetime(combined, errors="coerce")
        if parsed.notna().any():
            return parsed

    print("Warning: No recognisable timestamp column — treating file as one flight line.")
    return pd.Series(
        pd.date_range("2000-01-01", periods=len(data), freq="s"),
        index=data.index,
    )


def resolve_altitude_for_igrf(data, alt_amsl_col, alt_agl_col):
    """AMSL → AGL proxy → zeros, for IGRF altitude input."""
    for col in [alt_amsl_col, alt_agl_col]:
        if col and col in data.columns:
            vals = pd.to_numeric(data[col], errors="coerce")
            if vals.notna().any():
                if col == alt_agl_col and alt_amsl_col and alt_amsl_col not in data.columns:
                    print(f"Info: Using '{alt_agl_col}' as IGRF altitude proxy.")
                return vals.fillna(0).values
    print("Info: No altitude column for IGRF — using 0 m (error < 1 nT).")
    return np.zeros(len(data))


# ---------------------------------------------------------------------------
# Anomaly group processor (works on spatially-aggregated line data)
# ---------------------------------------------------------------------------

WARNING_FLAGS = {
    "short_window", "partial_halfwidth", "peak_mismatch",
    "negative_depth_A", "negative_depth_B", "method_disagreement",
    "narrow_anomaly", "wide_anomaly", "poor_trend_fit", "dipole_anomaly",
}


def _process_anomaly_group(
    g_start, g_end,
    anomaly_id,
    # aggregated line arrays
    agg_x, agg_tmi, agg_marks, agg_agl, agg_lat, agg_lon, agg_ts,
    agg_as,          # full-line AS array (same shape as agg_x), pre-computed
    # original (full-res) line arrays for writing output
    orig_line_idx, orig_x, orig_marks,
    poor_trend_fit,
    out_dist_a, out_depth_a, out_dist_b, out_depth_b,
    out_dist_mean, out_depth_mean, out_quality,
):
    """Process one contiguous marked group on an aggregated (spatial) grid.

    The Analytic Signal is NOT computed here — it is received as ``agg_as``,
    already calculated over the full flight line by ``compute_as``.  Only the
    window slice ``agg_as[win_idx]`` is used for depth estimation.
    """

    # Window around the marked group (in aggregated coordinates)
    x_start = agg_x[g_start] - WINDOW_PADDING_M
    x_end = agg_x[g_end] + WINDOW_PADDING_M
    win_mask = (agg_x >= x_start) & (agg_x <= x_end)
    win_idx = np.where(win_mask)[0]

    if len(win_idx) < MIN_WINDOW_SAMPLES:
        # Map back to original rows that are marked in this group
        grp_agg_x_start = agg_x[g_start]
        grp_agg_x_end = agg_x[g_end]
        orig_in_group = orig_line_idx[
            (orig_x >= grp_agg_x_start - SPATIAL_BIN_M) &
            (orig_x <= grp_agg_x_end + SPATIAL_BIN_M) &
            (orig_marks == 1)
        ]
        out_quality[orig_in_group] = "WARNING"
        return

    win_x = agg_x[win_idx]
    win_b = agg_tmi[win_idx]
    marked_in_win = agg_marks[win_idx] == 1

    # Extract the window slice of the full-line AS (no recomputation)
    AS = agg_as[win_idx]

    # Mark centre for local-peak search inside process_anomaly
    marked_x_in_win = agg_x[win_idx[marked_in_win]]
    mark_center_x = float(np.mean(marked_x_in_win)) if len(marked_x_in_win) > 0 else None

    result = process_anomaly(win_x, win_b, AS, marked_in_win,
                             mark_center_x=mark_center_x)
    flags = result["flags"]

    # Poor trend flag from original data
    grp_orig = orig_line_idx[
        (orig_x >= agg_x[g_start] - SPATIAL_BIN_M) &
        (orig_x <= agg_x[g_end] + SPATIAL_BIN_M)
    ]
    if np.any(poor_trend_fit[grp_orig]):
        flags.add("poor_trend_fit")

    # AGL for depth
    valid_agl = agg_agl[g_start:g_end + 1]
    valid_agl = valid_agl[~np.isnan(valid_agl)]
    mean_agl = float(np.mean(valid_agl)) if len(valid_agl) > 0 else np.nan

    def _depth(dist):
        if dist is None or math.isnan(mean_agl):
            return None, False
        d = dist - mean_agl
        return max(0.0, d), d < 0

    dist_a = result["distance_A"]
    dist_b = result["distance_B"]
    dist_mean = result["distance_mean"]
    depth_a_val, neg_a = _depth(dist_a)
    depth_b_val, neg_b = _depth(dist_b)

    if neg_a:
        flags.add("negative_depth_A")
    if neg_b:
        flags.add("negative_depth_B")

    depth_mean_val = None
    dist_mean_out = dist_mean
    is_dipole = "dipole_anomaly" in flags

    if depth_a_val is not None and depth_b_val is not None:
        if neg_b and not neg_a:
            # Method B distance is less than sensor AGL — physically impossible.
            # Method A gave a valid positive depth; use it alone.
            depth_mean_val = depth_a_val
            dist_mean_out = dist_a
        elif neg_a and not neg_b:
            # Symmetric case: Method A invalid, rely on Method B.
            depth_mean_val = depth_b_val
            dist_mean_out = dist_b
        elif dist_mean is not None:
            # Both methods produced valid positive depths.
            if (not is_dipole
                    and "method_disagreement" in flags
                    and dist_a is not None and dist_b is not None
                    and dist_a < dist_b):
                # The methods disagree and Method A gives a smaller distance.
                # For isolated (non-dipole) anomalies the local-halfwidth
                # Method A is typically more reliable; prefer it.
                depth_mean_val = depth_a_val
                dist_mean_out = dist_a
            else:
                depth_mean_val = (depth_a_val + depth_b_val) / 2.0

    # Fallback when only one method succeeded (or both were clipped to 0)
    if depth_mean_val is None:
        if depth_b_val is not None:
            depth_mean_val = depth_b_val
            dist_mean_out = dist_b
        elif depth_a_val is not None:
            depth_mean_val = depth_a_val
            dist_mean_out = dist_a

    quality = "WARNING" if flags & WARNING_FLAGS else "OK"

    # Write estimations to original marked rows
    grp_orig_marked = orig_line_idx[
        (orig_x >= agg_x[g_start] - SPATIAL_BIN_M) &
        (orig_x <= agg_x[g_end] + SPATIAL_BIN_M) &
        (orig_marks == 1)
    ]
    out_quality[grp_orig_marked] = quality
    if dist_a is not None:
        out_dist_a[grp_orig_marked] = round(dist_a, 4)
    if depth_a_val is not None:
        out_depth_a[grp_orig_marked] = round(depth_a_val, 4)
    if dist_b is not None:
        out_dist_b[grp_orig_marked] = round(dist_b, 4)
    if depth_b_val is not None:
        out_depth_b[grp_orig_marked] = round(depth_b_val, 4)
    if dist_mean_out is not None:
        out_dist_mean[grp_orig_marked] = round(dist_mean_out, 4)
    if depth_mean_val is not None:
        out_depth_mean[grp_orig_marked] = round(depth_mean_val, 4)



def _build_targets_path(input_path, output_dir):
    stem = normalize_input_stem(os.path.splitext(os.path.basename(input_path))[0])
    targets_name = stem + "-targets-as.csv"
    if not output_dir or not output_dir.strip():
        output_dir = os.path.dirname(os.path.abspath(input_path))
    output_dir = os.path.expanduser(os.path.expandvars(output_dir.strip()))
    os.makedirs(output_dir, exist_ok=True)
    return os.path.join(output_dir, targets_name)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Single-sensor magnetic depth estimation (Analytical Signal method)."
    )
    parser.add_argument("file_path", help="Input CSV file path")
    parser.add_argument("--mag-column", default="TMI",
                        help="TMI column name (default: TMI)")
    parser.add_argument("--altitude-amsl-column", default="",
                        help="Altitude AMSL column for IGRF (optional)")
    parser.add_argument("--altitude-agl-column", default="Altitude AGL",
                        help="Altitude AGL column (default: 'Altitude AGL')")
    parser.add_argument("--output-dir", dest="output_dir", default="",
                        help="Output directory for targets file")
    args = parser.parse_args()

    input_path = args.file_path
    mag_col = args.mag_column
    alt_amsl_col = args.altitude_amsl_column.strip()
    alt_agl_col = args.altitude_agl_column.strip()
    mark_col = MARK_COLUMN

    # ------------------------------------------------------------------
    # Load
    # ------------------------------------------------------------------
    print(f"Reading {input_path}")
    data = pd.read_csv(input_path, sep=CSV_SEPARATOR)
    # Own output columns that the script adds (or may have added on a previous run).
    # Exclude them so re-running on an already-processed file never duplicates columns.
    _SCRIPT_OUTPUT_COLS = {
        "TMI_anom", "IGRF_field", "Analytic_Signal",
        "Estimated_Distance_A", "Estimated_Depth_A",
        "Estimated_Distance_B", "Estimated_Depth_B",
        "Estimated_Distance", "Estimated_Depth",
        "Quality_Flag",
    }
    original_cols = [c for c in data.columns if c not in _SCRIPT_OUTPUT_COLS]

    missing = [c for c in [mag_col, "Latitude", "Longitude"] if c not in data.columns]
    if missing:
        print(f"Error: Missing required columns: {', '.join(missing)}")
        sys.exit(1)

    has_agl = bool(alt_agl_col and alt_agl_col in data.columns)

    # ------------------------------------------------------------------
    # Marks
    # ------------------------------------------------------------------
    if mark_col not in data.columns:
        print(f"No '{mark_col}' column found. Nothing to do.")
        sys.exit(0)

    marks = pd.to_numeric(data[mark_col], errors="coerce").fillna(0).values
    n_marked = int((marks == 1).sum())
    if n_marked == 0:
        print("No marked points (Mark = 1). Nothing to do.")
        sys.exit(0)
    print(f"Found {n_marked} marked point(s)")

    # ------------------------------------------------------------------
    # Timestamps, IGRF, anomaly
    # ------------------------------------------------------------------
    ts_series = resolve_timestamp(data)

    alt_for_igrf = resolve_altitude_for_igrf(data, alt_amsl_col or None, alt_agl_col or None)
    igrf_values = compute_igrf(
        data["Longitude"].values,
        data["Latitude"].values,
        alt_for_igrf,
        ts_series.values,
    )
    tmi_anom = pd.to_numeric(data[mag_col], errors="coerce").values - igrf_values

    # ------------------------------------------------------------------
    # AGL
    # ------------------------------------------------------------------
    agl_values = (pd.to_numeric(data[alt_agl_col], errors="coerce").values
                  if has_agl else np.full(len(data), np.nan))

    if (not has_agl) or np.all(np.isnan(agl_values[marks == 1])):
        print("Warning: Altitude AGL not available — "
              "Estimated Depth = sensor-to-target distance.")

    # ------------------------------------------------------------------
    # Along-track x
    # ------------------------------------------------------------------
    lats = pd.to_numeric(data["Latitude"], errors="coerce").values
    lons = pd.to_numeric(data["Longitude"], errors="coerce").values
    x_track = build_along_track(lats, lons)

    # ------------------------------------------------------------------
    # Flight lines
    # ------------------------------------------------------------------
    lines = split_into_lines(ts_series)
    print(f"Detected {len(lines)} flight line(s)")

    # ------------------------------------------------------------------
    # Output arrays
    # ------------------------------------------------------------------
    n_rows = len(data)
    out_tmi_anom = np.full(n_rows, np.nan)
    out_igrf = igrf_values.copy()
    out_as = np.full(n_rows, np.nan)
    out_dist_a = np.full(n_rows, np.nan)
    out_depth_a = np.full(n_rows, np.nan)
    out_dist_b = np.full(n_rows, np.nan)
    out_depth_b = np.full(n_rows, np.nan)
    out_dist_mean = np.full(n_rows, np.nan)
    out_depth_mean = np.full(n_rows, np.nan)
    out_quality = np.full(n_rows, "", dtype=object)
    poor_trend_fit = np.zeros(n_rows, dtype=bool)

    anomaly_id = 0

    # ------------------------------------------------------------------
    # Per-line processing
    # ------------------------------------------------------------------
    for line_idx in lines:
        line_marks = marks[line_idx]
        line_x = x_track[line_idx]
        line_tmi = tmi_anom[line_idx]
        line_agl = agl_values[line_idx]
        line_lat = lats[line_idx]
        line_lon = lons[line_idx]
        line_ts = ts_series.values[line_idx]

        # Trend removal on full-res data
        line_tmi_detrended, is_poor = remove_trend(line_tmi, line_x, line_marks)
        out_tmi_anom[line_idx] = line_tmi_detrended
        if is_poor:
            poor_trend_fit[line_idx] = True

        if not np.any(line_marks == 1):
            continue

        # Spatial aggregation — reduces ~5000 pts to ~hundreds of bins
        agg_x, agg_tmi, agg_marks, agg_agl, agg_lat, agg_lon, agg_ts = aggregate_spatial(
            line_x, line_tmi_detrended, line_marks,
            line_agl, line_lat, line_lon, line_ts,
        )

        # Compute AS ONCE over the full aggregated line (canonical approach:
        # FFT |k| is accurate when the domain >> individual anomaly halfwidth).
        agg_as = compute_as(agg_x, agg_tmi)

        # Write full-line AS back to original-resolution rows (nearest-bin mapping)
        agg_bin_idx = np.searchsorted(agg_x, line_x, side="left").clip(0, len(agg_x) - 1)
        out_as[line_idx] = np.round(agg_as[agg_bin_idx], 6)

        # Find contiguous marked groups in aggregated grid
        is_marked = (agg_marks == 1).astype(np.int8)
        starts = np.where(np.diff(np.concatenate([[0], is_marked])) == 1)[0]
        ends = np.where(np.diff(np.concatenate([is_marked, [0]])) == -1)[0]

        for g_start, g_end in zip(starts, ends):
            anomaly_id += 1
            _process_anomaly_group(
                g_start, g_end,
                anomaly_id,
                agg_x, agg_tmi, agg_marks, agg_agl, agg_lat, agg_lon, agg_ts,
                agg_as,          # full-line precomputed AS
                line_idx, line_x, line_marks,
                poor_trend_fit,
                out_dist_a, out_depth_a, out_dist_b, out_depth_b,
                out_dist_mean, out_depth_mean, out_quality,
            )

    # ------------------------------------------------------------------
    # Save
    # ------------------------------------------------------------------
    def _as_col(arr):
        out = arr.astype(object)
        out[np.isnan(arr)] = ""
        return out

    data["TMI_anom"] = _as_col(out_tmi_anom)
    data["IGRF_field"] = _as_col(out_igrf)
    data["Analytic_Signal"] = _as_col(out_as)
    data["Estimated_Distance_A"] = _as_col(out_dist_a)
    data["Estimated_Depth_A"] = _as_col(out_depth_a)
    data["Estimated_Distance_B"] = _as_col(out_dist_b)
    data["Estimated_Depth_B"] = _as_col(out_depth_b)
    data["Estimated_Distance"] = _as_col(out_dist_mean)
    data["Estimated_Depth"] = _as_col(out_depth_mean)
    data["Quality_Flag"] = out_quality

    print(f"Writing result to {input_path}")
    data.to_csv(input_path, index=False, sep=CSV_SEPARATOR)

    # Targets file: original columns + Estimated_Distance + Estimated_Depth,
    # one row per marked point (Mark == 1).
    targets_path = _build_targets_path(input_path, args.output_dir)
    target_mask = marks == 1
    targets_df = data.loc[target_mask, original_cols + [
        "Estimated_Distance_A", "Estimated_Depth_A",
        "Estimated_Distance_B", "Estimated_Depth_B",
        "Estimated_Distance", "Estimated_Depth",
    ]].copy()
    print(f"Writing targets to {targets_path}")
    targets_df.to_csv(targets_path, index=False, sep=CSV_SEPARATOR)
    print(f"Done. {anomaly_id} anomaly group(s) processed.")


if __name__ == "__main__":
    main()
