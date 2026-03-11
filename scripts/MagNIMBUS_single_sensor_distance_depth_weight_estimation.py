import sys
import os
import heapq
import argparse
import math
import numpy as np
import pandas as pd
from scipy.interpolate import griddata, RBFInterpolator
from scipy.spatial import cKDTree
from scipy.signal import butter, filtfilt
from script_utils import normalize_input_stem

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
MARK_COLUMN               = "Mark"
LINE_GAP_SECONDS          = 30
TREND_DEGREE              = 1
MIN_TREND_SAMPLES         = 10
WINDOW_PADDING_M          = 20.0
MIN_WINDOW_SAMPLES        = 20
K_FACTOR                  = 1.03       # FWHM → sensor-to-target distance (3D dipole)
CB_FACTOR                 = 3.0        # AS_max = 3·TMI/D  (vertical dipole)
AS_PEAK_SEARCH_RADIUS_M   = 5.0
ANOMALY_CENTER_RADIUS_M   = 15.0
DIPOLE_CHECK_RADIUS_M     = 15.0
METHOD_DISAGREE_THRESHOLD = 0.35
MIN_FWHM_M                = 0.5
MAX_FWHM_M                = 100.0       # m — full FWHM ceiling (K_FACTOR × FWHM ≤ MAX_DISTANCE_M)
SPATIAL_BIN_M             = 0.5
MIN_AS_THRESHOLD          = 1.0        # nT/m — below this, Method B is skipped
MAX_DISTANCE_M            = 20.0       # m    — implausible distance ceiling
BACKGROUND_METHOD         = "lowpass"
LOWPASS_WAVELENGTH_M      = 15.0       # m — spatial Butterworth cut-off wavelength
K_DIPOLE                  = 5e-3       # A·m²·nT⁻¹·m⁻³  (physical dipole constant)
DENSITY_STEEL             = 7800.0     # kg/m³
# Calibrated J_eff for sphere (geometric-mean fit to 5 steel objects, Brandenburg 2024).
# Accounts for remanent magnetisation (≈17.7× theoretical induced-only value of 119 A/m).
J_EFF_SPHERE_AM           = 2112.0     # A/m
# ±4.4 % in D  →  ((1+f)/(1−f))³ ≈ 1.30  →  ≈30 % weight range
WEIGHT_SPREAD_D_FRAC      = 0.044
# When dist_A > LARGE_FWHM_RATIO × dist_B, the FWHM search latched onto padding artefacts.
LARGE_FWHM_RATIO          = 2.5
# Asymmetry cap: trim the longer AS half-width when it exceeds the shorter by this ratio.
# Corrects geological gradients (observed raw ratio up to ~2.3, Brandenburg 2024 dataset).
ASYMMETRY_CAP_RATIO       = 1.20
CSV_SEPARATOR             = ","
DEFAULT_CELL_SIZE_M       = None

_SCRIPT_OUTPUT_COLS = {
    "TMI_LPF",
    "Estimated_Distance_Min", "Estimated_Depth_Min",
    "Estimated_Distance_Max", "Estimated_Depth_Max",
    "Estimated_Distance_Harmonic", "Estimated_Depth_Harmonic",
    "Estimated_Weight_Min", "Estimated_Weight_Max", "Estimated_Weight_Harmonic",
}


# ---------------------------------------------------------------------------
# Geometry helpers
# ---------------------------------------------------------------------------

def build_along_track(lats, lons):
    R = 6_371_000.0
    lat = np.radians(lats)
    lon = np.radians(lons)
    dlat, dlon = np.diff(lat), np.diff(lon)
    a = np.sin(dlat / 2) ** 2 + np.cos(lat[:-1]) * np.cos(lat[1:]) * np.sin(dlon / 2) ** 2
    return np.concatenate([[0.0], np.cumsum(R * 2 * np.arcsin(np.sqrt(np.clip(a, 0.0, 1.0))))])


def latlon_to_local_xy(lats, lons):
    """Equirectangular projection centred on the data midpoint. Returns (x_m, y_m)."""
    R = 6_371_000.0
    lat0, lon0 = np.nanmean(lats), np.nanmean(lons)
    x_m = R * np.radians(lons - lon0) * np.cos(np.radians(lat0))
    y_m = R * np.radians(lats - lat0)
    return x_m, y_m


# ---------------------------------------------------------------------------
# Flight-line splitting and spatial aggregation
# ---------------------------------------------------------------------------

def split_into_lines(ts_series, gap_seconds=LINE_GAP_SECONDS):
    ts_ns = pd.to_datetime(ts_series, errors="coerce").values.astype(np.int64)
    nat = np.iinfo(np.int64).min
    diffs = np.diff(ts_ns)
    gap_ns = int(gap_seconds * 1e9)
    is_break = (diffs > gap_ns) & (ts_ns[:-1] != nat) & (ts_ns[1:] != nat)
    starts = np.concatenate([[0], np.where(is_break)[0] + 1])
    ends   = np.concatenate([np.where(is_break)[0] + 1, [len(ts_ns)]])
    return [np.arange(s, e) for s, e in zip(starts, ends)]


def aggregate_spatial(x, tmi, marks, agl, bin_m=SPATIAL_BIN_M):
    """Bin data spatially and return aggregated arrays plus the per-row bin keys.

    Returns (agg_x, agg_tmi, agg_marks, agg_agl, bins) where bins has the same
    length as the input arrays and can be reused to bin additional arrays with
    the same grouping (e.g. pd.Series(other).groupby(bins, sort=True).mean()).
    """
    if len(x) == 0:
        return x, tmi, marks, agl, np.array([], dtype=np.int64)
    bins = np.floor(x / bin_m).astype(np.int64)
    df = pd.DataFrame({
        'bin': bins, 'x': x, 'tmi': tmi,
        'marks': marks.astype(bool), 'agl': agl,
    })
    g = df.groupby('bin', sort=True)
    out = pd.DataFrame({
        'x':     g['x'].mean(),
        'tmi':   g['tmi'].mean(),
        'marks': g['marks'].any().astype(float),
        'agl':   g['agl'].mean(),
    })
    return (out['x'].values, out['tmi'].values, out['marks'].values,
            out['agl'].values, bins)


# ---------------------------------------------------------------------------
# Background removal
# ---------------------------------------------------------------------------

def remove_background(tmi, x_pos, wavelength_m=LOWPASS_WAVELENGTH_M,
                      method=BACKGROUND_METHOD, marks=None,
                      degree=TREND_DEGREE, min_samples=MIN_TREND_SAMPLES):
    """
    Remove slowly-varying background from TMI along one flight line.

    method='linear'  — polynomial fit to unmarked points (legacy).
    method='lowpass' — spatial 4th-order zero-phase Butterworth LP filter
                       resampled to a uniform grid, then a rolling spatial
                       median, interpolated back to original positions.

    Returns (tmi_detrended, is_poor_fit).
    """
    if method == "linear":
        unmarked = (marks == 0) if marks is not None else np.ones(len(tmi), dtype=bool)
        if unmarked.sum() < min_samples:
            return tmi.copy(), True
        coeffs = np.polyfit(x_pos[unmarked], tmi[unmarked], degree)
        return tmi - np.polyval(coeffs, x_pos), False

    n = len(tmi)
    if n < 4:
        return tmi.copy(), True

    x0, x1 = float(x_pos[0]), float(x_pos[-1])
    track_len = x1 - x0
    if track_len <= 0.0 or track_len < wavelength_m * 0.5:
        return tmi - np.nanmean(tmi), True

    DS = 0.5  # uniform resample spacing (m)
    x_u   = np.arange(x0, x1 + DS, DS)
    tmi_u = np.interp(x_u, x_pos, tmi)

    Wn = min(2.0 * DS / wavelength_m, 0.99)
    b_f, a_f = butter(4, Wn, btype="low")
    if len(tmi_u) < 3 * max(len(a_f), len(b_f)):
        return tmi - np.nanmean(tmi), True

    bg_lp = filtfilt(b_f, a_f, tmi_u)
    win = max(3, int(round(wavelength_m / DS)))
    bg = (pd.Series(bg_lp).rolling(win, center=True, min_periods=1).median().to_numpy())
    return tmi - np.interp(x_pos, x_u, bg), False


# ---------------------------------------------------------------------------
# FWHM half-width
# ---------------------------------------------------------------------------

def find_local_halfwidth(x_arr, y_arr, peak_idx):
    """Return (left_x, right_x) at the half-power threshold around peak_idx."""
    threshold = y_arr[peak_idx] / 2.0

    below_left = np.where(y_arr[:peak_idx] <= threshold)[0]
    if below_left.size:
        i = below_left[-1]
        denom = y_arr[i + 1] - y_arr[i]
        t = (threshold - y_arr[i]) / denom if denom != 0 else 0.0
        left_x = x_arr[i] + t * (x_arr[i + 1] - x_arr[i])
    else:
        left_x = None

    below_right = np.where(y_arr[peak_idx + 1:] <= threshold)[0]
    if below_right.size:
        i = peak_idx + 1 + below_right[0]
        denom = y_arr[i] - y_arr[i - 1]
        t = (threshold - y_arr[i - 1]) / denom if denom != 0 else 0.0
        right_x = x_arr[i - 1] + t * (x_arr[i] - x_arr[i - 1])
    else:
        right_x = None

    return left_x, right_x


# ---------------------------------------------------------------------------
# Grid construction and interpolation
# ---------------------------------------------------------------------------

def estimate_cell_size(x_m, y_m, lines):
    """
    Estimate grid cell size from data spacing.
    Uses median non-zero along-track segment length per line; falls back to
    extent/168 (~0.6 m for a 100 m survey).  Result is snapped to nearest 0.1 m.
    """
    def _snap(v):
        return max(0.5, round(v, 1))

    all_nonzero = []
    for idx in lines:
        if len(idx) < 2:
            continue
        seg = np.sqrt(np.diff(x_m[idx]) ** 2 + np.diff(y_m[idx]) ** 2)
        nonzero = seg[seg > 0.01]
        if len(nonzero) > 0:
            all_nonzero.append(np.median(nonzero))
    if all_nonzero:
        cs = float(np.median(all_nonzero))
        if 0.5 <= cs <= 10.0:
            return _snap(cs)

    dx, dy = np.ptp(x_m), np.ptp(y_m)
    extent = max(dx, dy)
    return _snap(extent / 168.0) if extent > 0 else 1.0


def build_2d_grid(x_m, y_m, values, cell_size, blanking_distance=None):
    """
    Build a regular 2D grid from scattered data, matching Java GriddingService:
      1. Grid dimensions from extent / cellSize (integer truncation).
      2. Median per bin.
      3. Fill missing visible cells: global median → thin-plate spline (RBFInterpolator)
         with linear griddata fallback.
      4. Clip interpolated cells to ±100 % of measured range.
      5. Blank cells outside blanking_distance (default: 10 × cell_size).

    Returns (grid [H×W], xi [W], yi [H]).
    """
    if cell_size <= 0:
        raise ValueError(f"cell_size must be positive, got {cell_size}")

    valid = np.isfinite(values)
    xv, yv, vv = x_m[valid], y_m[valid], values[valid]
    if len(xv) == 0:
        raise ValueError("No valid data points for gridding")

    x_min, x_max = float(xv.min()), float(xv.max())
    y_min, y_max = float(yv.min()), float(yv.max())

    grid_width  = max(2, int((x_max - x_min) / cell_size))
    grid_height = max(2, int((y_max - y_min) / cell_size))

    lon_step = (x_max - x_min) / (grid_width  - 1)
    lat_step = (y_max - y_min) / (grid_height - 1)
    xi = np.linspace(x_min, x_max, grid_width)
    yi = np.linspace(y_min, y_max, grid_height)

    ix = np.clip(((xv - x_min) / lon_step if lon_step > 0 else np.zeros(len(xv))).astype(int),
                 0, grid_width - 1)
    iy = np.clip(((yv - y_min) / lat_step if lat_step > 0 else np.zeros(len(yv))).astype(int),
                 0, grid_height - 1)
    cell_key = iy * grid_width + ix
    medians = pd.Series(vv).groupby(cell_key).median()
    grid = np.full(grid_height * grid_width, np.nan)
    grid[medians.index.values] = medians.values
    grid = grid.reshape(grid_height, grid_width)

    d_iy, d_ix = np.unravel_index(medians.index.values, (grid_height, grid_width))
    data_pts = np.column_stack([xi[d_ix], yi[d_iy]])

    if blanking_distance is None:
        blanking_distance = cell_size * 10
    gy_all, gx_all = np.mgrid[0:grid_height, 0:grid_width]
    grid_pts = np.column_stack([xi[gx_all.ravel()], yi[gy_all.ravel()]])
    dists, _ = cKDTree(data_pts).query(grid_pts)
    visible = (dists <= blanking_distance).reshape(grid_height, grid_width)

    global_median = float(np.nanmedian(grid[np.isfinite(grid)]))
    missing = np.isnan(grid) & visible
    grid[missing] = global_median
    needs_interp = missing

    if needs_interp.any() and np.isfinite(grid).sum() >= 10:
        known = ~needs_interp & np.isfinite(grid)
        if known.sum() >= 10:
            gy, gx = np.mgrid[0:grid_height, 0:grid_width]
            pts_known   = np.column_stack([xi[gx[known]],        yi[gy[known]]])
            vals_known  = grid[known]
            pts_missing = np.column_stack([xi[gx[needs_interp]], yi[gy[needs_interp]]])
            try:
                n_known   = len(pts_known)
                nbrs      = None if n_known <= 1000 else min(n_known, 150)
                smoothing = float(np.var(vals_known)) * 1e-3
                rbf    = RBFInterpolator(pts_known, vals_known,
                                         kernel='thin_plate_spline',
                                         smoothing=smoothing, neighbors=nbrs)
                filled = rbf(pts_missing)
                still_nan = np.isnan(filled)
                if still_nan.any():
                    filled[still_nan] = griddata(pts_known, vals_known,
                                                  pts_missing[still_nan], method="linear")
                grid[needs_interp] = filled
            except Exception:
                try:
                    grid[needs_interp] = griddata(pts_known, vals_known,
                                                   pts_missing, method="linear")
                except Exception:
                    pass

    if needs_interp.any():
        v_known = grid[~needs_interp & np.isfinite(grid)]
        if v_known.size:
            v_lo, v_hi = float(v_known.min()), float(v_known.max())
            v_rng = max(abs(v_hi - v_lo), 1.0)
            filled_mask = needs_interp & np.isfinite(grid)
            grid[filled_mask] = np.clip(grid[filled_mask], v_lo - v_rng, v_hi + v_rng)

    grid[~visible] = np.nan
    return grid, xi, yi


def _fill_nan_priority_queue(grid, cell_width, cell_height):
    """
    Fill NaN cells with IDW 8-neighbour averaging via a max-priority queue.
    Exact reproduction of Java GridInterpolator.interpolate().
    """
    g = grid.copy()
    m, n = g.shape
    offsets = [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)]
    offset_dist = {(di, dj): math.sqrt((di * cell_width) ** 2 + (dj * cell_height) ** 2)
                   for di, dj in offsets}

    def _neighbour_sum(i, j):
        s = ws = 0.0
        for di, dj in offsets:
            ni, nj = i + di, j + dj
            if 0 <= ni < m and 0 <= nj < n:
                v = g[ni, nj]
                if np.isfinite(v):
                    w = 1.0 / offset_dist[(di, dj)]
                    s += v * w; ws += w
        return s, ws

    neighbor_weights = np.zeros((m, n))
    heap = []
    for i in range(m):
        for j in range(n):
            if np.isfinite(g[i, j]):
                continue
            _, ws = _neighbour_sum(i, j)
            if ws > 0.0:
                neighbor_weights[i, j] = ws
                heapq.heappush(heap, (-ws, i, j))

    while heap:
        neg_w, i, j = heapq.heappop(heap)
        if np.isfinite(g[i, j]):
            continue
        s, ws = _neighbour_sum(i, j)
        g[i, j] = s / ws if ws > 0 else np.nan
        for di, dj in offsets:
            ni, nj = i + di, j + dj
            if 0 <= ni < m and 0 <= nj < n and not np.isfinite(g[ni, nj]):
                increment = 1.0 / offset_dist[(di, dj)]
                neighbor_weights[ni, nj] += increment
                heapq.heappush(heap, (-neighbor_weights[ni, nj], ni, nj))

    return g


def sample_grid_at_points(grid, xi, yi, x_m, y_m):
    """Bilinear sampling of a regular grid at arbitrary (x_m, y_m) positions."""
    dx = xi[1] - xi[0] if len(xi) > 1 else 1.0
    dy = yi[1] - yi[0] if len(yi) > 1 else 1.0
    fi = np.clip((x_m - xi[0]) / dx, 0, grid.shape[1] - 1.0001)
    fj = np.clip((y_m - yi[0]) / dy, 0, grid.shape[0] - 1.0001)
    i0, j0 = np.floor(fi).astype(int), np.floor(fj).astype(int)
    i1 = np.minimum(i0 + 1, grid.shape[1] - 1)
    j1 = np.minimum(j0 + 1, grid.shape[0] - 1)
    wx, wy = fi - i0, fj - j0
    return (grid[j0, i0] * (1-wx) * (1-wy) + grid[j0, i1] * wx * (1-wy) +
            grid[j1, i0] * (1-wx) * wy   + grid[j1, i1] * wx * wy)


# ---------------------------------------------------------------------------
# 2D Analytic Signal — matching Java AnalyticSignalFilter exactly
# ---------------------------------------------------------------------------

def _grid_x_derivative(grid, cell_width):
    """5-point stencil → 3-point central → forward/backward at edges."""
    m, n = grid.shape
    dx = np.full((m, n), np.nan)

    if n >= 5:
        j = slice(2, n-2)
        vals = np.stack([grid[:, 0:n-4], grid[:, 1:n-3], grid[:, 3:n-1], grid[:, 4:n]])
        result = (-grid[:, 4:n] + 8*grid[:, 3:n-1] - 8*grid[:, 1:n-3] + grid[:, 0:n-4]) / (12*cell_width)
        dx[:, j] = np.where(np.all(np.isfinite(vals), axis=0), result, np.nan)

    if n >= 3:
        j = slice(1, n-1)
        need_fill = np.isnan(dx[:, j])
        both_valid = np.isfinite(grid[:, 0:n-2]) & np.isfinite(grid[:, 2:n])
        central = (grid[:, 2:n] - grid[:, 0:n-2]) / (2*cell_width)
        dx_sub = dx[:, j].copy()
        dx_sub[need_fill & both_valid] = central[need_fill & both_valid]
        dx[:, j] = dx_sub

    if n >= 2:
        mask = np.isfinite(grid[:, 0]) & np.isfinite(grid[:, 1]) & np.isnan(dx[:, 0])
        dx[mask, 0] = (grid[mask, 1] - grid[mask, 0]) / cell_width
        mask = np.isfinite(grid[:, n-1]) & np.isfinite(grid[:, n-2]) & np.isnan(dx[:, n-1])
        dx[mask, n-1] = (grid[mask, n-1] - grid[mask, n-2]) / cell_width
    return dx


def _grid_y_derivative(grid, cell_height):
    """5-point stencil → 3-point central → forward/backward at edges."""
    m, n = grid.shape
    dy = np.full((m, n), np.nan)

    if m >= 5:
        i = slice(2, m-2)
        vals = np.stack([grid[0:m-4, :], grid[1:m-3, :], grid[3:m-1, :], grid[4:m, :]])
        result = (-grid[4:m, :] + 8*grid[3:m-1, :] - 8*grid[1:m-3, :] + grid[0:m-4, :]) / (12*cell_height)
        dy[i, :] = np.where(np.all(np.isfinite(vals), axis=0), result, np.nan)

    if m >= 3:
        i = slice(1, m-1)
        need_fill = np.isnan(dy[i, :])
        both_valid = np.isfinite(grid[0:m-2, :]) & np.isfinite(grid[2:m, :])
        central = (grid[2:m, :] - grid[0:m-2, :]) / (2*cell_height)
        dy_sub = dy[i, :].copy()
        dy_sub[need_fill & both_valid] = central[need_fill & both_valid]
        dy[i, :] = dy_sub

    if m >= 2:
        mask = np.isfinite(grid[0, :]) & np.isfinite(grid[1, :]) & np.isnan(dy[0, :])
        dy[0, mask] = (grid[1, mask] - grid[0, mask]) / cell_height
        mask = np.isfinite(grid[m-1, :]) & np.isfinite(grid[m-2, :]) & np.isnan(dy[m-1, :])
        dy[m-1, mask] = (grid[m-1, mask] - grid[m-2, mask]) / cell_height
    return dy


def _grid_z_derivative(grid, cell_width, cell_height):
    """
    Vertical derivative via 2D FFT — matches Java AnalyticSignalFilter.getZDerivativeMatrix():
    NaN→0, FFT, multiply by |k|=sqrt(kx²+ky²), IFFT, real part.
    """
    m, n = grid.shape
    g = np.where(np.isfinite(grid), grid, 0.0)
    G = np.fft.fft2(g)

    dkx = 2.0 * np.pi / (n * cell_width)
    dky = 2.0 * np.pi / (m * cell_height)
    kx_idx = np.where(np.arange(n) <= n//2, np.arange(n), np.arange(n) - n)
    ky_idx = np.where(np.arange(m) <= m//2, np.arange(m), np.arange(m) - m)
    KX, KY = np.meshgrid(kx_idx * dkx, ky_idx * dky)

    return np.real(np.fft.ifft2(G * np.sqrt(KX**2 + KY**2)))


def compute_as_2d(grid, cell_width, cell_height):
    """
    2D Analytic Signal magnitude — matches Java AnalyticSignalFilter:
      1. Fill NaN gaps (priority-queue IDW, Java GridInterpolator.interpolate).
      2. dz via 2D FFT; dx, dy via 5-point stencil.
      3. AS = sqrt(dx²+dy²+dz²); original NaN cells stay NaN.
    """
    orig_nan_mask = ~np.isfinite(grid)
    grid_filled = _fill_nan_priority_queue(grid, cell_width, cell_height)
    dx = _grid_x_derivative(grid_filled, cell_width)
    dy = _grid_y_derivative(grid_filled, cell_height)
    dz = _grid_z_derivative(grid_filled, cell_width, cell_height)
    AS = np.sqrt(dx**2 + dy**2 + dz**2)
    AS[orig_nan_mask] = np.nan
    return AS


# ---------------------------------------------------------------------------
# Anomaly processing
# ---------------------------------------------------------------------------

def process_anomaly(window_x, window_b, AS, marked_mask_in_window, mark_center_x=None):
    flags = set()
    n = len(window_x)
    edge = max(1, int(n * 0.10))
    if n - 2 * edge < 1:
        flags.add("short_window")
        return dict(distance_A=None, distance_B=None,
                    b_max_nT=None, as_max_nT_m=None,
                    as_peak_idx=None, flags=flags)

    AS_valid = AS[edge:-edge]
    x_valid  = window_x[edge:-edge]

    marked_x_arr = window_x[marked_mask_in_window]
    if mark_center_x is None:
        mark_center_x = (float(np.mean(marked_x_arr)) if len(marked_x_arr) > 0
                         else float(np.median(window_x)))

    near_mask = ((x_valid >= mark_center_x - ANOMALY_CENTER_RADIUS_M) &
                 (x_valid <= mark_center_x + ANOMALY_CENTER_RADIUS_M))
    if near_mask.any():
        near_indices  = np.where(near_mask)[0]
        as_peak_local = int(near_indices[int(np.argmax(AS_valid[near_mask]))])
    else:
        as_peak_local = int(np.argmax(AS_valid))

    AS_max      = float(AS_valid[as_peak_local])
    as_peak_idx = edge + as_peak_local

    # Method A: FWHM → d_A = K_FACTOR × FWHM
    left_x, right_x = find_local_halfwidth(x_valid, AS_valid, as_peak_local)
    distance_A = None

    if left_x is None or right_x is None:
        flags.add("partial_halfwidth")
    else:
        peak_x   = float(x_valid[as_peak_local])
        left_hw  = peak_x - float(left_x)
        right_hw = float(right_x) - peak_x
        if left_hw > 0 and right_hw > 0:
            if left_hw > right_hw * ASYMMETRY_CAP_RATIO:
                left_hw = right_hw * ASYMMETRY_CAP_RATIO
                left_x  = peak_x - left_hw
            elif right_hw > left_hw * ASYMMETRY_CAP_RATIO:
                right_hw = left_hw * ASYMMETRY_CAP_RATIO
                right_x  = peak_x + right_hw
        W = right_x - left_x
        if W < MIN_FWHM_M:
            flags.add("narrow_anomaly")
        elif W > MAX_FWHM_M:
            flags.add("wide_anomaly")
        distance_A = K_FACTOR * W
        if distance_A > MAX_DISTANCE_M:
            flags.add("wide_anomaly")
            distance_A = None

    # Method B: AS ratio  d_B = C_B · |B(x_AS_peak)| / AS(x_AS_peak)
    b_at_as_peak = float(window_b[as_peak_idx])
    b_max_nT     = b_at_as_peak
    distance_B   = None

    if AS_max < MIN_AS_THRESHOLD:
        flags.add("peak_mismatch")
    elif abs(b_at_as_peak) < 1.0:
        flags.add("peak_mismatch")
    else:
        distance_B = CB_FACTOR * abs(b_at_as_peak) / AS_max
        if distance_B > MAX_DISTANCE_M:
            flags.add("peak_mismatch")
            distance_B = None

    # Dipole check
    dipole_mask = ((window_x >= mark_center_x - DIPOLE_CHECK_RADIUS_M) &
                   (window_x <= mark_center_x + DIPOLE_CHECK_RADIUS_M))
    if dipole_mask.any():
        near_b_vals = window_b[dipole_mask]
        b_pos, b_neg = float(np.max(near_b_vals)), float(np.min(near_b_vals))
        if b_pos > 0 and b_neg < -0.15 * b_pos:
            flags.add("dipole_anomaly")

    if (distance_A is not None and distance_B is not None
            and "partial_halfwidth" not in flags and "peak_mismatch" not in flags
            and distance_A > 0
            and abs(distance_A - distance_B) / max(distance_A, distance_B) > METHOD_DISAGREE_THRESHOLD):
        flags.add("method_disagreement")

    return dict(
        distance_A=distance_A, distance_B=distance_B,
        b_max_nT=b_max_nT if "peak_mismatch" not in flags else None,
        as_max_nT_m=AS_max, as_peak_idx=as_peak_idx, flags=flags,
    )


# ---------------------------------------------------------------------------
# Timestamp / altitude resolution
# ---------------------------------------------------------------------------

def resolve_timestamp(data):
    if "Timestamp" in data.columns:
        return pd.to_datetime(data["Timestamp"], errors="coerce")
    if "Date" in data.columns and "Time" in data.columns:
        combined = data["Date"].astype(str).str.strip() + " " + data["Time"].astype(str).str.strip()
        parsed = pd.to_datetime(combined, errors="coerce")
        if parsed.notna().any():
            return parsed
    print("Warning: No recognisable timestamp column — treating file as one flight line.")
    return pd.Series(pd.date_range("2000-01-01", periods=len(data), freq="s"), index=data.index)


# ---------------------------------------------------------------------------
# Weight estimation
# ---------------------------------------------------------------------------

def estimate_weight(b_max_nt, distance_m, density):
    """
    Estimate source weight from peak field and sensor-to-source distance.

    J_eff = J_EFF_SPHERE_AM (calibrated for steel sphere, includes remanence).
    Returns weight_kg (float) or None.
    """
    if b_max_nt is None or distance_m is None or b_max_nt <= 0.0:
        return None
    m_moment  = K_DIPOLE * b_max_nt * (distance_m ** 3)
    weight_kg = density * m_moment / J_EFF_SPHERE_AM
    return round(weight_kg, 2)


# ---------------------------------------------------------------------------
# Anomaly group processor
# ---------------------------------------------------------------------------

def _process_anomaly_group(
        g_start, g_end,
        agg_x, agg_tmi, agg_marks, agg_agl,
        agg_as,
        orig_line_idx, orig_x, orig_marks,
        density,
        out_dist_min, out_depth_min, out_dist_max, out_depth_max,
        out_dist_avg, out_depth_avg,
        out_weight_min, out_weight_max, out_weight_avg,
):
    x_start = agg_x[g_start] - WINDOW_PADDING_M
    x_end   = agg_x[g_end]   + WINDOW_PADDING_M
    win_idx = np.where((agg_x >= x_start) & (agg_x <= x_end))[0]

    if len(win_idx) < MIN_WINDOW_SAMPLES:
        return

    win_x         = agg_x[win_idx]
    win_b         = agg_tmi[win_idx]
    marked_in_win = agg_marks[win_idx] == 1
    AS            = agg_as[win_idx]

    marked_x_in_win = agg_x[win_idx[marked_in_win]]
    mark_center_x   = float(np.mean(marked_x_in_win)) if len(marked_x_in_win) > 0 else None

    result = process_anomaly(win_x, win_b, AS, marked_in_win, mark_center_x=mark_center_x)

    valid_agl = agg_agl[g_start:g_end + 1]
    valid_agl = valid_agl[~np.isnan(valid_agl)]
    mean_agl  = float(np.mean(valid_agl)) if len(valid_agl) > 0 else np.nan

    def _depth(dist):
        if dist is None or math.isnan(mean_agl):
            return None
        return max(0.0, dist - mean_agl)

    dist_a, dist_b = result["distance_A"], result["distance_B"]

    # Distance range: [min(A, B), max(A, B)]
    valid_dists = [d for d in (dist_a, dist_b) if d is not None]
    if valid_dists:
        dist_min_out, dist_max_out = min(valid_dists), max(valid_dists)
    else:
        dist_min_out = dist_max_out = None
    depth_min_out = _depth(dist_min_out)
    depth_max_out = _depth(dist_max_out)

    # Best-estimate distance — rule-based with harmonic-mean fallback:
    #   Rule 1: dist_B < AGL  → target sub-sensor, trust FWHM (dist_A)
    #   Rule 2: dist_A > 2.5×dist_B  → FWHM inflated by window artefact, trust dist_B
    #   Rule 3: dist_B < dist_A  → AS inflated by geology, trust dist_A
    #   Fallback: harmonic mean (pulled toward the smaller, more reliable value)
    if dist_a is not None and dist_b is not None:
        if not math.isnan(mean_agl) and dist_b < mean_agl:
            dist_avg_out = dist_a
        elif dist_a > LARGE_FWHM_RATIO * dist_b:
            dist_avg_out = dist_b
        elif dist_b < dist_a:
            dist_avg_out = dist_a
        else:
            _sum = dist_a + dist_b
            dist_avg_out = 2.0 * dist_a * dist_b / _sum if _sum > 0.0 else dist_a
    else:
        dist_avg_out = dist_a if dist_a is not None else dist_b
    depth_avg_out = _depth(dist_avg_out)

    grp_orig_marked = orig_line_idx[
        (orig_x >= agg_x[g_start] - SPATIAL_BIN_M) &
        (orig_x <= agg_x[g_end]   + SPATIAL_BIN_M) &
        (orig_marks == 1)
    ]
    for arr, val in ((out_dist_min,  dist_min_out),  (out_depth_min,  depth_min_out),
                     (out_dist_max,  dist_max_out),  (out_depth_max,  depth_max_out),
                     (out_dist_avg,  dist_avg_out),  (out_depth_avg,  depth_avg_out)):
        if val is not None:
            arr[grp_orig_marked] = round(val, 4)

    # Weight estimation (B_max at AS-peak; fallback to neighbourhood search)
    b_at_as_peak_val = result["b_max_nT"]
    if b_at_as_peak_val is not None:
        b_max_nt = abs(b_at_as_peak_val)
    elif mark_center_x is not None:
        near_mark = np.abs(win_x - mark_center_x) <= AS_PEAK_SEARCH_RADIUS_M
        if near_mark.any():
            b_max_nt = float(np.max(np.abs(win_b[near_mark])))
        elif marked_in_win.any():
            b_max_nt = float(np.max(np.abs(win_b[marked_in_win])))
        else:
            b_max_nt = None
    else:
        marked_tmi = win_b[marked_in_win]
        b_max_nt   = float(np.max(np.abs(marked_tmi))) if marked_tmi.size else None

    # When Rule 3 fired (AS inflated by geology), use dist_B so that the
    # B/AS ratio self-consistently cancels the geological inflation.
    _rule3_fired = (
        dist_a is not None and dist_b is not None
        and not math.isnan(mean_agl)
        and mean_agl <= dist_b < dist_a <= LARGE_FWHM_RATIO * dist_b
    )
    if _rule3_fired:
        dist_weight = dist_b
    elif dist_avg_out is not None and not math.isnan(mean_agl):
        dist_weight = max(dist_avg_out, mean_agl)
    else:
        dist_weight = dist_avg_out

    w_avg = estimate_weight(b_max_nt, dist_weight, density)
    if dist_weight is not None:
        w_min = estimate_weight(b_max_nt, dist_weight * (1.0 - WEIGHT_SPREAD_D_FRAC), density)
        w_max = estimate_weight(b_max_nt, dist_weight * (1.0 + WEIGHT_SPREAD_D_FRAC), density)
    else:
        w_min = w_max = None
    for arr, val in ((out_weight_min, w_min), (out_weight_max, w_max), (out_weight_avg, w_avg)):
        if val is not None:
            arr[grp_orig_marked] = val


# ---------------------------------------------------------------------------
# Output path helper
# ---------------------------------------------------------------------------

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
        description="Single-sensor magnetic distance/depth/weight estimation (2D Analytic Signal)."
    )
    parser.add_argument("file_path",                  help="Input CSV file path")
    parser.add_argument("--mag-column",               default="TMI",
                        help="TMI column name (default: TMI)")
    parser.add_argument("--altitude-agl-column",      default="Altitude AGL",
                        help="Altitude AGL column (default: 'Altitude AGL')")
    parser.add_argument("--output-dir",               dest="output_dir", default="",
                        help="Output directory for targets file")
    args = parser.parse_args()

    input_path  = args.file_path
    mag_col     = args.mag_column
    alt_agl_col = args.altitude_agl_column.strip()

    print(f"Reading {input_path}")
    try:
        data = pd.read_csv(input_path, sep=CSV_SEPARATOR)
    except FileNotFoundError:
        print(f"Error: File not found: {input_path}")
        sys.exit(1)
    except Exception as e:
        print(f"Error: Failed to read CSV: {e}")
        sys.exit(1)
    original_cols = [c for c in data.columns if c not in _SCRIPT_OUTPUT_COLS]
    data = data[original_cols]

    missing = [c for c in [mag_col, "Latitude", "Longitude"] if c not in data.columns]
    if missing:
        print(f"Error: Missing required columns: {', '.join(missing)}")
        sys.exit(1)

    has_agl = bool(alt_agl_col and alt_agl_col in data.columns)

    if MARK_COLUMN not in data.columns:
        print(f"No '{MARK_COLUMN}' column found. Nothing to do.")
        sys.exit(0)

    marks    = pd.to_numeric(data[MARK_COLUMN], errors="coerce").fillna(0).values
    n_marked = int((marks == 1).sum())
    if n_marked == 0:
        print("No marked points (Mark = 1). Nothing to do.")
        sys.exit(0)
    print(f"Found {n_marked} marked point(s)")

    ts_series  = resolve_timestamp(data)
    tmi_raw    = pd.to_numeric(data[mag_col], errors="coerce").values
    agl_values = (pd.to_numeric(data[alt_agl_col], errors="coerce").values
                  if has_agl else np.full(len(data), np.nan))

    if (not has_agl) or np.all(np.isnan(agl_values[marks == 1])):
        print("Warning: Altitude AGL not available — Estimated Depth = sensor-to-target distance.")

    lats    = pd.to_numeric(data["Latitude"],  errors="coerce").values
    lons    = pd.to_numeric(data["Longitude"], errors="coerce").values
    x_track = build_along_track(lats, lons)
    x_m, y_m = latlon_to_local_xy(lats, lons)

    lines = split_into_lines(ts_series)
    print(f"Detected {len(lines)} flight line(s)")

    # Per-line background removal
    n_rows        = len(data)
    tmi_detrended = np.full(n_rows, np.nan)
    tmi_lpf       = np.full(n_rows, np.nan)

    for line_idx in lines:
        line_tmi = tmi_raw[line_idx]
        line_tmi_dt, _ = remove_background(line_tmi, x_track[line_idx],
                                            marks=marks[line_idx])
        tmi_detrended[line_idx] = line_tmi_dt
        tmi_lpf[line_idx]       = line_tmi - line_tmi_dt

    # Build 2D grid and compute Analytic Signal
    cell_size = DEFAULT_CELL_SIZE_M or estimate_cell_size(x_m, y_m, lines)
    try:
        grid, xi, yi = build_2d_grid(x_m, y_m, tmi_detrended, cell_size)
    except ValueError as e:
        print(f"Error: Failed to build grid: {e}")
        sys.exit(1)
    cell_x = float(xi[1] - xi[0]) if len(xi) > 1 else cell_size
    cell_y = float(yi[1] - yi[0]) if len(yi) > 1 else cell_size
    as_grid      = compute_as_2d(grid, cell_x, cell_y)
    as_at_points = sample_grid_at_points(as_grid, xi, yi, x_m, y_m)

    # Output arrays
    out_dist_min   = np.full(n_rows, np.nan)
    out_depth_min  = np.full(n_rows, np.nan)
    out_dist_max   = np.full(n_rows, np.nan)
    out_depth_max  = np.full(n_rows, np.nan)
    out_dist_avg   = np.full(n_rows, np.nan)
    out_depth_avg  = np.full(n_rows, np.nan)
    out_weight_min = np.full(n_rows, np.nan)
    out_weight_max = np.full(n_rows, np.nan)
    out_weight_avg = np.full(n_rows, np.nan)
    anomaly_id     = 0

    for line_idx in lines:
        line_marks = marks[line_idx]
        if not np.any(line_marks == 1):
            continue

        line_x   = x_track[line_idx]
        line_tmi = tmi_detrended[line_idx]
        line_agl = agl_values[line_idx]
        line_as  = as_at_points[line_idx]

        agg_x, agg_tmi, agg_marks, agg_agl, _bins = aggregate_spatial(
            line_x, line_tmi, line_marks, line_agl)
        agg_as = pd.Series(line_as).groupby(_bins, sort=True).mean().values

        is_marked = (agg_marks == 1).astype(np.int8)
        starts = np.where(np.diff(np.concatenate([[0], is_marked])) == 1)[0]
        ends   = np.where(np.diff(np.concatenate([is_marked, [0]])) == -1)[0]

        for g_start, g_end in zip(starts, ends):
            anomaly_id += 1
            _process_anomaly_group(
                g_start, g_end,
                agg_x, agg_tmi, agg_marks, agg_agl,
                agg_as,
                line_idx, line_x, line_marks,
                DENSITY_STEEL,
                out_dist_min, out_depth_min, out_dist_max, out_depth_max,
                out_dist_avg, out_depth_avg,
                out_weight_min, out_weight_max, out_weight_avg,
            )

    # Save results
    def _as_col(arr):
        out = arr.astype(object)
        out[np.isnan(arr)] = ""
        return out

    data["TMI_LPF"]                     = tmi_lpf
    data["Estimated_Distance_Min"]      = _as_col(out_dist_min)
    data["Estimated_Distance_Max"]      = _as_col(out_dist_max)
    data["Estimated_Distance_Harmonic"] = _as_col(out_dist_avg)
    data["Estimated_Depth_Min"]         = _as_col(out_depth_min)
    data["Estimated_Depth_Max"]         = _as_col(out_depth_max)
    data["Estimated_Depth_Harmonic"]    = _as_col(out_depth_avg)
    data["Estimated_Weight_Min"]        = _as_col(out_weight_min)
    data["Estimated_Weight_Max"]        = _as_col(out_weight_max)
    data["Estimated_Weight_Harmonic"]   = _as_col(out_weight_avg)

    print(f"Writing result to {input_path}")
    data.to_csv(input_path, index=False, sep=CSV_SEPARATOR)

    targets_path = _build_targets_path(input_path, args.output_dir)
    target_mask  = marks == 1
    targets_df   = data.loc[target_mask, original_cols + [
        "TMI_LPF",
        "Estimated_Distance_Min", "Estimated_Distance_Max", "Estimated_Distance_Harmonic",
        "Estimated_Depth_Min",    "Estimated_Depth_Max",    "Estimated_Depth_Harmonic",
        "Estimated_Weight_Min",   "Estimated_Weight_Max",   "Estimated_Weight_Harmonic",
    ]].copy()
    print(f"Writing targets to {targets_path}")
    targets_df.to_csv(targets_path, index=False, sep=CSV_SEPARATOR)

    print(f"Done. {anomaly_id} anomaly group(s) processed.")


if __name__ == "__main__":
    main()
