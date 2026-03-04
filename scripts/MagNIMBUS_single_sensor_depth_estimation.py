import sys
import os
import argparse
import math
import numpy as np
import pandas as pd
from scipy.interpolate import griddata, RBFInterpolator
from scipy.spatial import cKDTree
from scipy.ndimage import uniform_filter
from script_utils import normalize_input_stem

try:
    import ppigrf
except ImportError:
    ppigrf = None

try:
    import matplotlib
    matplotlib.use("Agg")          # headless — no display required
    import matplotlib.pyplot as plt
    import matplotlib.patheffects as pe
    _MATPLOTLIB_OK = True
except ImportError:
    _MATPLOTLIB_OK = False

# Internal constants (not exposed as CLI args)
MARK_COLUMN = "Mark"
LINE_GAP_SECONDS = 30
TREND_DEGREE = 1
MIN_TREND_SAMPLES = 10
WINDOW_PADDING_M = 20.0
MIN_WINDOW_SAMPLES = 20
K_FACTOR = 1.0
CB_FACTOR = 3.0
MAX_PEAK_OFFSET_M = 2.0
AS_PEAK_SEARCH_RADIUS_M = 5.0
DIPOLE_CHECK_RADIUS_M = 15.0
METHOD_DISAGREE_THRESHOLD = 0.35
MIN_HALFWIDTH_M = 0.5
MAX_HALFWIDTH_M = 50.0
SPATIAL_BIN_M = 0.5
MIN_AS_THRESHOLD = 1.0      # nT/m — AS values below this are noise; Method B is skipped
MAX_DISTANCE_M   = 20.0     # m    — distances above this are discarded as implausible
DEPTH_WARNING_THRESHOLD_M = 2.0  # m — estimated depths above this get Quality_Flag = WARNING
CSV_SEPARATOR = ","
# Default grid cell size (metres).  If not supplied, estimated from data.
DEFAULT_CELL_SIZE_M = None



# ---------------------------------------------------------------------------
# Helpers (unchanged from original)
# ---------------------------------------------------------------------------

def build_along_track(lats, lons):
    R = 6_371_000.0
    lat = np.radians(lats)
    lon = np.radians(lons)
    dlat = np.diff(lat)
    dlon = np.diff(lon)
    a = np.sin(dlat / 2) ** 2 + np.cos(lat[:-1]) * np.cos(lat[1:]) * np.sin(dlon / 2) ** 2
    seg = R * 2 * np.arcsin(np.sqrt(np.clip(a, 0.0, 1.0)))
    return np.concatenate([[0.0], np.cumsum(seg)])


def compute_igrf(lons, lats, alts_m, timestamps):
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
    ts_ns = pd.to_datetime(ts_series, errors="coerce").values.astype(np.int64)
    nat = np.iinfo(np.int64).min
    diffs = np.diff(ts_ns)
    gap_ns = int(gap_seconds * 1e9)
    is_break = (diffs > gap_ns) & (ts_ns[:-1] != nat) & (ts_ns[1:] != nat)
    starts = np.concatenate([[0], np.where(is_break)[0] + 1])
    ends = np.concatenate([np.where(is_break)[0] + 1, [len(ts_ns)]])
    return [np.arange(s, e) for s, e in zip(starts, ends)]


def aggregate_spatial(x, tmi, marks, agl, lat, lon, ts, bin_m=SPATIAL_BIN_M):
    if len(x) == 0:
        return x, tmi, marks, agl, lat, lon, ts
    bins = np.floor(x / bin_m).astype(np.int64)
    df = pd.DataFrame({
        'bin': bins,
        'x': x, 'tmi': tmi,
        'marks': marks.astype(bool),
        'agl': agl, 'lat': lat, 'lon': lon,
        '_pos': np.arange(len(x), dtype=np.intp),
    })
    g = df.groupby('bin', sort=True)
    out = pd.DataFrame({
        'x':     g['x'].mean(),
        'tmi':   g['tmi'].mean(),           # pandas skips NaN by default
        'marks': g['marks'].any().astype(float),
        'agl':   g['agl'].mean(),           # nanmean equivalent
        'lat':   g['lat'].mean(),
        'lon':   g['lon'].mean(),
    })
    out_ts = ts[g['_pos'].first().values]
    return (
        out['x'].values, out['tmi'].values, out['marks'].values,
        out['agl'].values, out['lat'].values, out['lon'].values, out_ts,
    )


def remove_trend(tmi_anom, x, marks, degree=TREND_DEGREE, min_samples=MIN_TREND_SAMPLES):
    unmarked = marks == 0
    if unmarked.sum() < min_samples:
        return tmi_anom.copy(), True
    coeffs = np.polyfit(x[unmarked], tmi_anom[unmarked], degree)
    return tmi_anom - np.polyval(coeffs, x), False


def find_local_halfwidth(x_arr, y_arr, peak_idx):
    """Return (left_x, right_x) at the half-power threshold around peak_idx."""
    threshold = y_arr[peak_idx] / 2.0

    # Left: rightmost index left of peak that drops to/below threshold
    below_left = np.where(y_arr[:peak_idx] <= threshold)[0]
    if below_left.size:
        i = below_left[-1]
        denom = y_arr[i + 1] - y_arr[i]
        t = (threshold - y_arr[i]) / denom if denom != 0 else 0.0
        left_x = x_arr[i] + t * (x_arr[i + 1] - x_arr[i])
    else:
        left_x = None

    # Right: leftmost index right of peak that drops to/below threshold
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
# Coordinate conversion: lat/lon → local XY (metres)
# ---------------------------------------------------------------------------

def latlon_to_local_xy(lats, lons):
    """
    Equirectangular projection centred on the data midpoint.
    Returns (x_m, y_m, lat0, lon0) where x_m/y_m are in metres.
    """
    R = 6_371_000.0
    lat0 = np.nanmean(lats)
    lon0 = np.nanmean(lons)
    x_m = R * np.radians(lons - lon0) * np.cos(np.radians(lat0))
    y_m = R * np.radians(lats - lat0)
    return x_m, y_m, lat0, lon0


# ---------------------------------------------------------------------------
# 2D Grid construction and interpolation
# ---------------------------------------------------------------------------

def estimate_cell_size(x_m, y_m, lines):
    """
    Estimate a reasonable grid cell size from the data.

    Strategy:
      1. Estimate the GPS position quantisation step from the distribution of
         unique X-coordinate values (the minimum non-zero spacing between
         sorted unique positions, at the 5th percentile to be robust to
         occasional larger gaps).  This gives the E-W GPS resolution in
         metres, which is the natural cell size that avoids chequerboard
         aliasing.
      2. Fall back to ``extent / 168`` (~0.6 m for a 100 m survey) if the
         per-line spacing estimate is unreliable (outside [0.5 m, 10 m]).

    The validity window [0.5 m, 10 m] prevents absurdly small values caused
    by sub-centimetre GPS jitter or absurdly large values from coarse sensors.
    """
    def _snap(v):
        """Round to the nearest 0.1 m to reduce grid-alignment aliasing."""
        return max(0.5, round(v, 1))

    # --- Approach 1: median non-zero along-track spacing per line ---
    all_nonzero = []
    for idx in lines:
        if len(idx) < 2:
            continue
        dx = np.diff(x_m[idx])
        dy = np.diff(y_m[idx])
        seg = np.sqrt(dx ** 2 + dy ** 2)
        nonzero = seg[seg > 0.01]
        if len(nonzero) > 0:
            all_nonzero.append(np.median(nonzero))
    if all_nonzero:
        cs = float(np.median(all_nonzero))
        if 0.5 <= cs <= 10.0:
            return _snap(cs)

    # --- Fallback: extent / 168 (~0.6 m for a 100 m survey area) ---
    dx = np.ptp(x_m)
    dy = np.ptp(y_m)
    extent = max(dx, dy)
    if extent > 0:
        return _snap(extent / 168.0)
    return 1.0


def build_2d_grid(x_m, y_m, values, cell_size, blanking_distance=None):
    """
    Build a regular 2D grid from scattered data.
    Reproduces the Java GriddingService pipeline:

      1. Compute grid dimensions:  gridWidth  = extent_x / cellSize
                                   gridHeight = extent_y / cellSize
      2. Bin data into grid cells; take median per cell.
      3. Mark missing cells (boolean mask ``m``).
      4. Fill missing cells with blanking-distance-limited median, then
         interpolate with minimum-curvature (thin-plate spline via
         RBFInterpolator).  Falls back to linear griddata on failure.
      5. Set cells outside blanking distance (circular) to NaN.

    The resulting grid (with possible NaN at edges) is later passed to
    compute_as_2d, which internally calls _fill_nan_priority_queue
    (matching Java GridInterpolator.interpolate inside AnalyticSignalFilter)
    before computing derivatives.

    Returns:
        grid : 2D numpy array [gridHeight, gridWidth]  (rows=Y, cols=X)
        xi   : 1D array of grid X centres (length gridWidth)
        yi   : 1D array of grid Y centres (length gridHeight)
    """
    if cell_size <= 0:
        raise ValueError(f"cell_size must be positive, got {cell_size}")

    valid = np.isfinite(values)
    xv, yv, vv = x_m[valid], y_m[valid], values[valid]
    if len(xv) == 0:
        raise ValueError("No valid data points for gridding")

    x_min, x_max = float(xv.min()), float(xv.max())
    y_min, y_max = float(yv.min()), float(yv.max())

    # Grid dimensions — matches Java:
    #   gridWidth  = (int)(distance(minLon→maxLon) / cellSize)
    #   gridHeight = (int)(distance(minLat→maxLat) / cellSize)
    grid_width = max(2, int((x_max - x_min) / cell_size))
    grid_height = max(2, int((y_max - y_min) / cell_size))

    print(f"  Grid: {grid_width} × {grid_height} cells "
          f"({grid_width * grid_height} total)")

    lon_step = (x_max - x_min) / (grid_width - 1)
    lat_step = (y_max - y_min) / (grid_height - 1)

    xi = np.linspace(x_min, x_max, grid_width)
    yi = np.linspace(y_min, y_max, grid_height)

    # --- Bin data into grid cells and take median per cell (Java: HashMap) ---
    ix = np.clip(((xv - x_min) / lon_step if lon_step > 0
                  else np.zeros(len(xv))).astype(int), 0, grid_width - 1)
    iy = np.clip(((yv - y_min) / lat_step if lat_step > 0
                  else np.zeros(len(yv))).astype(int), 0, grid_height - 1)
    cell_key = iy * grid_width + ix
    medians = pd.Series(vv).groupby(cell_key).median()
    grid = np.full(grid_height * grid_width, np.nan)
    grid[medians.index.values] = medians.values
    grid = grid.reshape(grid_height, grid_width)

    # Physical (x, y) position of every cell that has measured data.
    d_iy, d_ix = np.unravel_index(medians.index.values, (grid_height, grid_width))
    data_pts = np.column_stack([xi[d_ix], yi[d_iy]])

    # --- Circular blanking via KDTree (Euclidean distance in metres) ---
    if blanking_distance is None:
        blanking_distance = cell_size * 10

    gy_all, gx_all = np.mgrid[0:grid_height, 0:grid_width]
    grid_pts = np.column_stack([xi[gx_all.ravel()], yi[gy_all.ravel()]])
    dists, _ = cKDTree(data_pts).query(grid_pts)
    visible = (dists <= blanking_distance).reshape(grid_height, grid_width)

    # --- Fill missing cells with global median, then interpolate ---
    # Java fills missing visible cells with median before calling
    # SplinesGridder2.gridMissing().
    global_median = float(np.nanmedian(grid[np.isfinite(grid)]))
    missing = np.isnan(grid) & visible
    grid[missing] = global_median

    # Mark which cells need interpolation (True = missing = needs filling)
    needs_interp = missing  # cells that were NaN and got median-filled

    if needs_interp.any() and np.isfinite(grid).sum() >= 10:
        # Minimum-curvature interpolation via thin-plate spline (Briggs 1974).
        # Equivalent to solving the biharmonic equation ∇⁴f = 0 in the gaps —
        # the standard potential-field gridding approach.
        known = ~needs_interp & np.isfinite(grid)
        if known.sum() >= 10:
            gy, gx = np.mgrid[0:grid_height, 0:grid_width]
            # Use physical coordinates (metres) for correct spatial weighting.
            pts_known   = np.column_stack([xi[gx[known]],         yi[gy[known]]])
            vals_known  = grid[known]
            pts_missing = np.column_stack([xi[gx[needs_interp]],  yi[gy[needs_interp]]])
            try:
                n_known = len(pts_known)
                # For large grids use local neighbours to avoid O(N²) memory.
                nbrs = None if n_known <= 1000 else min(n_known, 150)
                # Light regularisation (~0.1 % of variance) stabilises the TPS
                # on noisy field data without visibly distorting the surface.
                smoothing = float(np.var(vals_known)) * 1e-3
                rbf = RBFInterpolator(pts_known, vals_known,
                                      kernel='thin_plate_spline',
                                      smoothing=smoothing,
                                      neighbors=nbrs)
                filled = rbf(pts_missing)
                # TPS should not produce NaN; fall back to linear if it does.
                still_nan = np.isnan(filled)
                if still_nan.any():
                    filled2 = griddata(pts_known, vals_known,
                                       pts_missing[still_nan], method="linear")
                    filled[still_nan] = filled2
                grid[needs_interp] = filled
            except Exception:
                # Fall back to linear griddata if TPS fails (degenerate geometry).
                try:
                    filled = griddata(pts_known, vals_known,
                                      pts_missing, method="linear")
                    grid[needs_interp] = filled
                except Exception:
                    pass

    # --- Clip interpolated cells to ±100% of measured data range ---
    # TPS and linear extrapolation can overshoot at grid edges, creating
    # steep gradients that inflate AS values.  Clamping to the measured
    # range keeps the filled surface physically plausible.
    if needs_interp.any():
        v_known = grid[~needs_interp & np.isfinite(grid)]
        if v_known.size:
            v_lo = float(v_known.min())
            v_hi = float(v_known.max())
            v_rng = max(abs(v_hi - v_lo), 1.0)
            filled_mask = needs_interp & np.isfinite(grid)
            grid[filled_mask] = np.clip(grid[filled_mask],
                                        v_lo - v_rng, v_hi + v_rng)

    # --- Apply blanking: cells outside visible range → NaN ---
    grid[~visible] = np.nan

    return grid, xi, yi


def _fill_nan_priority_queue(grid, cell_width, cell_height):
    """
    Fill NaN cells using inverse-distance-weighted 8-neighbour averaging
    with a priority queue.  Exact reproduction of Java GridInterpolator.

    Algorithm:
      1. For every NaN cell, compute the sum of weights (1/dist) of its
         non-NaN 8-neighbours.  If > 0, push (cell, weight_sum) into a
         max-priority queue.
      2. Pop the cell with the highest neighbour weight sum.
         - If it was already filled (duplicate entry), skip.
         - Otherwise set it to the IDW average of its non-NaN neighbours.
         - For each still-NaN neighbour, increment its weight sum by
           1/dist to the newly filled cell and push it into the queue.
      3. Repeat until the queue is empty.
    """
    import heapq

    g = grid.copy()
    m, n = g.shape

    # 8-neighbour offsets
    offsets = [(-1, -1), (-1, 0), (-1, 1),
               (0, -1),          (0, 1),
               (1, -1),  (1, 0), (1, 1)]

    # Pre-compute distances for each offset
    offset_dist = {}
    for di, dj in offsets:
        dx = di * cell_width
        dy = dj * cell_height
        offset_dist[(di, dj)] = math.sqrt(dx * dx + dy * dy)

    def _neighbour_sum(i, j):
        """IDW sum and weight-sum of non-NaN 8-neighbours."""
        s = 0.0
        ws = 0.0
        for di, dj in offsets:
            ni, nj = i + di, j + dj
            if 0 <= ni < m and 0 <= nj < n:
                v = g[ni, nj]
                if np.isfinite(v):
                    w = 1.0 / offset_dist[(di, dj)]
                    s += v * w
                    ws += w
        return s, ws

    # Initialise: compute neighbour weights for all NaN cells
    neighbor_weights = np.zeros((m, n))
    # heapq is a min-heap; negate weight to get max-priority behaviour
    heap = []

    for i in range(m):
        for j in range(n):
            if np.isfinite(g[i, j]):
                continue
            _, ws = _neighbour_sum(i, j)
            if ws > 0.0:
                neighbor_weights[i, j] = ws
                heapq.heappush(heap, (-ws, i, j))

    # Process queue
    while heap:
        neg_w, i, j = heapq.heappop(heap)

        # Cell may have been filled by an earlier (higher-priority) pop
        if np.isfinite(g[i, j]):
            continue

        s, ws = _neighbour_sum(i, j)
        g[i, j] = s / ws if ws > 0 else np.nan

        # Update NaN neighbours
        for di, dj in offsets:
            ni, nj = i + di, j + dj
            if 0 <= ni < m and 0 <= nj < n:
                if np.isfinite(g[ni, nj]):
                    continue
                increment = 1.0 / offset_dist[(di, dj)]
                neighbor_weights[ni, nj] += increment
                heapq.heappush(heap, (-neighbor_weights[ni, nj], ni, nj))

    return g


def sample_grid_at_points(grid, xi, yi, x_m, y_m):
    """
    Bilinear sampling of a regular grid at arbitrary (x_m, y_m) positions.
    Returns 1D array of sampled values with same length as x_m.
    """
    # Convert coordinates to fractional grid indices
    dx = xi[1] - xi[0] if len(xi) > 1 else 1.0
    dy = yi[1] - yi[0] if len(yi) > 1 else 1.0
    fi = (x_m - xi[0]) / dx  # fractional column index
    fj = (y_m - yi[0]) / dy  # fractional row index

    m, n = grid.shape
    # Clamp to grid bounds
    fi = np.clip(fi, 0, n - 1.0001)
    fj = np.clip(fj, 0, m - 1.0001)

    i0 = np.floor(fi).astype(int)
    j0 = np.floor(fj).astype(int)
    i1 = np.minimum(i0 + 1, n - 1)
    j1 = np.minimum(j0 + 1, m - 1)

    wx = fi - i0
    wy = fj - j0

    # Bilinear interpolation
    val = (grid[j0, i0] * (1 - wx) * (1 - wy) +
           grid[j0, i1] * wx * (1 - wy) +
           grid[j1, i0] * (1 - wx) * wy +
           grid[j1, i1] * wx * wy)

    return val


# ---------------------------------------------------------------------------
# 2D Analytic Signal — matching Java AnalyticSignalFilter exactly
# ---------------------------------------------------------------------------

def _grid_x_derivative(grid, cell_width):
    """
    X-derivative matching Java AnalyticSignalFilter.getXDerivative():
    5-point stencil → 3-point central → forward/backward at edges.
    """
    m, n = grid.shape
    dx = np.full((m, n), np.nan)

    # 5-point stencil: columns 2..n-3
    if n >= 5:
        j = slice(2, n - 2)
        vals = np.stack([grid[:, 0:n-4], grid[:, 1:n-3], grid[:, 3:n-1], grid[:, 4:n]])
        all_valid = np.all(np.isfinite(vals), axis=0)
        result = (-grid[:, 4:n] + 8.0 * grid[:, 3:n-1]
                  - 8.0 * grid[:, 1:n-3] + grid[:, 0:n-4]) / (12.0 * cell_width)
        dx[:, j] = np.where(all_valid, result, np.nan)

    # 3-point central: columns 1..n-2 where 5-point didn't fill
    if n >= 3:
        j = slice(1, n - 1)
        need_fill = np.isnan(dx[:, j])
        both_valid = np.isfinite(grid[:, 0:n-2]) & np.isfinite(grid[:, 2:n])
        fill_mask = need_fill & both_valid
        central = (grid[:, 2:n] - grid[:, 0:n-2]) / (2.0 * cell_width)
        dx_sub = dx[:, j].copy()
        dx_sub[fill_mask] = central[fill_mask]
        dx[:, j] = dx_sub

    # Forward difference: column 0
    if n >= 2:
        valid = np.isfinite(grid[:, 0]) & np.isfinite(grid[:, 1])
        still_nan = np.isnan(dx[:, 0])
        mask = valid & still_nan
        dx[mask, 0] = (grid[mask, 1] - grid[mask, 0]) / cell_width

    # Backward difference: column n-1
    if n >= 2:
        valid = np.isfinite(grid[:, n-1]) & np.isfinite(grid[:, n-2])
        still_nan = np.isnan(dx[:, n-1])
        mask = valid & still_nan
        dx[mask, n-1] = (grid[mask, n-1] - grid[mask, n-2]) / cell_width

    return dx


def _grid_y_derivative(grid, cell_height):
    """
    Y-derivative matching Java AnalyticSignalFilter.getYDerivative():
    5-point stencil → 3-point central → forward/backward at edges.
    """
    m, n = grid.shape
    dy = np.full((m, n), np.nan)

    if m >= 5:
        i = slice(2, m - 2)
        vals = np.stack([grid[0:m-4, :], grid[1:m-3, :], grid[3:m-1, :], grid[4:m, :]])
        all_valid = np.all(np.isfinite(vals), axis=0)
        result = (-grid[4:m, :] + 8.0 * grid[3:m-1, :]
                  - 8.0 * grid[1:m-3, :] + grid[0:m-4, :]) / (12.0 * cell_height)
        dy[i, :] = np.where(all_valid, result, np.nan)

    if m >= 3:
        i = slice(1, m - 1)
        need_fill = np.isnan(dy[i, :])
        both_valid = np.isfinite(grid[0:m-2, :]) & np.isfinite(grid[2:m, :])
        fill_mask = need_fill & both_valid
        central = (grid[2:m, :] - grid[0:m-2, :]) / (2.0 * cell_height)
        dy_sub = dy[i, :].copy()
        dy_sub[fill_mask] = central[fill_mask]
        dy[i, :] = dy_sub

    if m >= 2:
        valid = np.isfinite(grid[0, :]) & np.isfinite(grid[1, :])
        still_nan = np.isnan(dy[0, :])
        mask = valid & still_nan
        dy[0, mask] = (grid[1, mask] - grid[0, mask]) / cell_height

    if m >= 2:
        valid = np.isfinite(grid[m-1, :]) & np.isfinite(grid[m-2, :])
        still_nan = np.isnan(dy[m-1, :])
        mask = valid & still_nan
        dy[m-1, mask] = (grid[m-1, mask] - grid[m-2, mask]) / cell_height

    return dy


def _grid_z_derivative(grid, cell_width, cell_height):
    """
    Vertical derivative via 2D FFT.
    Matches Java AnalyticSignalFilter.getZDerivativeMatrix() exactly:

      1. Pack grid into complex array (NaN → 0)
      2. Forward 2D FFT
      3. Multiply by |k| = sqrt(kx² + ky²) where:
            dkx = 2π / (n · cellWidth)
            dky = 2π / (m · cellHeight)
            kxIndex = j  if j ≤ n/2  else j - n
            kyIndex = i  if i ≤ m/2  else i - m
      4. Inverse 2D FFT (normalised)
      5. Take real part

    Returns 2D array same shape as grid.
    """
    m, n = grid.shape

    # Replace NaN with 0 for FFT (matches Java: NaN → 0 in complexGrid init)
    g = np.where(np.isfinite(grid), grid, 0.0)

    # Forward 2D FFT
    G = np.fft.fft2(g)

    # Build |k| multiplier matching Java frequency ordering
    dkx = 2.0 * np.pi / (n * cell_width)
    dky = 2.0 * np.pi / (m * cell_height)

    # kxIndex: j if j <= n//2 else j - n  →  same as np.fft.fftfreq * n
    # kyIndex: i if i <= m//2 else i - m  →  same as np.fft.fftfreq * m
    kx_idx = np.where(np.arange(n) <= n // 2,
                      np.arange(n),
                      np.arange(n) - n)
    ky_idx = np.where(np.arange(m) <= m // 2,
                      np.arange(m),
                      np.arange(m) - m)

    kx = kx_idx * dkx  # shape (n,)
    ky = ky_idx * dky  # shape (m,)

    # |k| grid  — shape (m, n)
    KX, KY = np.meshgrid(kx, ky)
    K = np.sqrt(KX ** 2 + KY ** 2)

    # Multiply spectrum by |k|
    G *= K

    # Inverse FFT (normalised) — take real part
    dz = np.real(np.fft.ifft2(G))

    return dz


def compute_as_2d(grid, cell_width, cell_height):
    """
    2D Analytic Signal magnitude grid.
    Matches Java AnalyticSignalFilter exactly:

      1. GridInterpolator.interpolate() — fill NaN gaps (priority-queue IDW)
         This happens in the AnalyticSignalFilter constructor.
      2. dz via 2D FFT (getZDerivativeMatrix)
      3. dx, dy via 5-point stencil (getXDerivative, getYDerivative)
      4. AS = sqrt(dx² + dy² + dz²)

    NaN cells from the ORIGINAL grid (before interpolation) produce NaN in
    the output, matching Java's check: if (Float.isNaN(gridOrigin[i][j]))
    """
    m, n = grid.shape
    print(f"  Computing 2D AS on grid ({m} × {n})...")

    # Remember which cells were originally NaN (Java: gridOrigin)
    orig_nan_mask = ~np.isfinite(grid)

    # Step 1: Fill NaN gaps — matches Java GridInterpolator.interpolate()
    # called inside AnalyticSignalFilter constructor on this.grid (the copy)
    grid_filled = _fill_nan_priority_queue(grid, cell_width, cell_height)

    # Step 2-3: Compute derivatives on the filled grid
    dx = _grid_x_derivative(grid_filled, cell_width)
    dy = _grid_y_derivative(grid_filled, cell_height)

    dz = _grid_z_derivative(grid_filled, cell_width, cell_height)

    # Step 4: AS = sqrt(dx² + dy² + dz²)
    AS = np.sqrt(dx ** 2 + dy ** 2 + dz ** 2)

    # Java: if (Float.isNaN(gridOrigin[i][j])) magnitude = NaN
    AS[orig_nan_mask] = np.nan

    return AS


# ---------------------------------------------------------------------------
# process_anomaly and helpers (unchanged except AS is now from grid)
# ---------------------------------------------------------------------------

def process_anomaly(window_x, window_b, AS, marked_mask_in_window,
                    mark_center_x=None):
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

    marked_x_arr = window_x[marked_mask_in_window]
    if mark_center_x is None:
        mark_center_x = (float(np.mean(marked_x_arr)) if len(marked_x_arr) > 0
                         else float(np.median(window_x)))

    near_mask = ((x_valid >= mark_center_x - AS_PEAK_SEARCH_RADIUS_M) &
                 (x_valid <= mark_center_x + AS_PEAK_SEARCH_RADIUS_M))
    if near_mask.any():
        near_indices = np.where(near_mask)[0]
        as_peak_local = int(near_indices[int(np.argmax(AS_valid[near_mask]))])
    else:
        as_peak_local = int(np.argmax(AS_valid))

    AS_max = float(AS_valid[as_peak_local])
    as_peak_idx = edge + as_peak_local

    # --- Method A: Half-Width ---
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
        if distance_A > MAX_DISTANCE_M:
            flags.add("wide_anomaly")
            distance_A = None

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
        if AS_at_peak < MIN_AS_THRESHOLD:
            flags.add("peak_mismatch")
        else:
            distance_B = CB_FACTOR * abs(b_max_nT) / AS_at_peak
            if distance_B > MAX_DISTANCE_M:
                flags.add("peak_mismatch")
                distance_B = None

    # --- Dipole check ---
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
# Timestamp / altitude resolution (unchanged)
# ---------------------------------------------------------------------------

def resolve_timestamp(data):
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
# Anomaly group processor (uses grid-sampled AS)
# ---------------------------------------------------------------------------

def _process_anomaly_group(
        g_start, g_end, anomaly_id,
        agg_x, agg_tmi, agg_marks, agg_agl, agg_lat, agg_lon, agg_ts,
        agg_as,
        orig_line_idx, orig_x, orig_marks,
        poor_trend_fit,
        out_dist_a, out_depth_a, out_dist_b, out_depth_b,
        out_dist_mean, out_depth_mean, out_quality, out_error_pct,
):
    x_start = agg_x[g_start] - WINDOW_PADDING_M
    x_end = agg_x[g_end] + WINDOW_PADDING_M
    win_mask = (agg_x >= x_start) & (agg_x <= x_end)
    win_idx = np.where(win_mask)[0]

    if len(win_idx) < MIN_WINDOW_SAMPLES:
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
    AS = agg_as[win_idx]

    marked_x_in_win = agg_x[win_idx[marked_in_win]]
    mark_center_x = float(np.mean(marked_x_in_win)) if len(marked_x_in_win) > 0 else None

    result = process_anomaly(win_x, win_b, AS, marked_in_win,
                             mark_center_x=mark_center_x)
    flags = result["flags"]

    grp_orig = orig_line_idx[
        (orig_x >= agg_x[g_start] - SPATIAL_BIN_M) &
        (orig_x <= agg_x[g_end] + SPATIAL_BIN_M)
        ]
    if np.any(poor_trend_fit[grp_orig]):
        flags.add("poor_trend_fit")

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
            depth_mean_val = depth_a_val
            dist_mean_out = dist_a
        elif neg_a and not neg_b:
            depth_mean_val = depth_b_val
            dist_mean_out = dist_b
        elif dist_mean is not None:
            if (not is_dipole
                    and "method_disagreement" in flags
                    and dist_a is not None and dist_b is not None):
                if dist_a < dist_b:
                    # Narrow FWHM (e.g. interference from a nearby anomaly)
                    # makes Method A underestimate — trust Method B instead.
                    depth_mean_val = depth_b_val
                    dist_mean_out = dist_b
                else:
                    # Inflated AS (e.g. grid interpolation noise) makes
                    # Method B underestimate — trust Method A instead.
                    depth_mean_val = depth_a_val
                    dist_mean_out = dist_a
            else:
                depth_mean_val = (depth_a_val + depth_b_val) / 2.0

    if depth_mean_val is None:
        if depth_b_val is not None:
            depth_mean_val = depth_b_val
            dist_mean_out = dist_b
        elif depth_a_val is not None:
            depth_mean_val = depth_a_val
            dist_mean_out = dist_a

    # Quality flag: WARNING if depth could not be estimated or exceeds threshold.
    quality = ("WARNING"
               if depth_mean_val is None or depth_mean_val > DEPTH_WARNING_THRESHOLD_M
               else "OK")

    # Error percent: estimated depth as a percentage of the 2 m warning threshold.
    # 100 % = exactly at the limit; > 100 % = over the limit (only written for WARNING).
    error_pct_val = None
    if depth_mean_val is not None:
        error_pct_val = round(depth_mean_val / DEPTH_WARNING_THRESHOLD_M * 100.0, 1)

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
    if error_pct_val is not None and quality == "WARNING":
        out_error_pct[grp_orig_marked] = error_pct_val


def visualize_results(
        grid, xi, yi,
        x_m, y_m, marks,
        out_dist_a, out_depth_a,
        out_dist_b, out_depth_b,
        out_dist_mean, out_depth_mean,
        out_quality,
        png_path,
):
    """
    Save a 2×3 figure showing the TMI anomaly grid as background and the
    estimated depth / distance values labelled at each target location.

    Layout:
        Row 1 :  Estimated_Depth_A   |  Estimated_Depth_B   |  Estimated_Depth
        Row 2 :  Estimated_Distance_A | Estimated_Distance_B | Estimated_Distance
    """
    if not _MATPLOTLIB_OK:
        print("Warning: matplotlib not installed — skipping visualisation.")
        return

    from scipy.ndimage import gaussian_filter
    from matplotlib.colors import LightSource, Normalize
    from matplotlib.lines import Line2D

    SMOOTH_SIGMA = 1.5
    VERT_EXAG    = 3.0

    target_mask = marks == 1
    tx = x_m[target_mask]
    ty = y_m[target_mask]
    t_quality = out_quality[target_mask]

    panels = [
        (out_depth_a[target_mask],    "Estimated_Depth_A"),
        (out_depth_b[target_mask],    "Estimated_Depth_B"),
        (out_depth_mean[target_mask], "Estimated_Depth"),
        (out_dist_a[target_mask],     "Estimated_Distance_A"),
        (out_dist_b[target_mask],     "Estimated_Distance_B"),
        (out_dist_mean[target_mask],  "Estimated_Distance"),
    ]

    # ── Smooth grid (NaN-safe: fill → smooth → restore NaN) ───────────────
    nan_mask  = ~np.isfinite(grid)
    fill_val  = float(np.nanmedian(grid)) if np.isfinite(grid).any() else 0.0
    grid_fill = np.where(nan_mask, fill_val, grid)
    grid_smooth = gaussian_filter(grid_fill, sigma=SMOOTH_SIGMA)
    grid_smooth[nan_mask] = np.nan

    # ── Colour normalisation (symmetric, 98th-percentile clip) ────────────
    finite = grid_smooth[np.isfinite(grid_smooth)]
    vmax = float(np.percentile(np.abs(finite), 98)) if finite.size else 1.0
    norm = Normalize(vmin=-vmax, vmax=vmax)
    cmap = plt.cm.RdYlGn_r

    # ── Hillshaded RGBA — computed once, reused across all panels ──────────
    ls          = LightSource(azdeg=315, altdeg=45)
    shade_input = np.where(np.isfinite(grid_smooth), grid_smooth, fill_val)
    rgb_hs      = ls.shade(shade_input, cmap=cmap, norm=norm,
                           blend_mode="overlay", vert_exag=VERT_EXAG)

    # ScalarMappable for colourbar — created once, shared across panels ─────
    sm = plt.cm.ScalarMappable(cmap=cmap, norm=norm)
    sm.set_array([])

    # ── Figure size: preserve true spatial aspect ratio ────────────────────
    x_range = max(float(xi[-1] - xi[0]), 1.0)
    y_range = max(float(yi[-1] - yi[0]), 1.0)
    panel_w = 6.0
    panel_h = max(panel_w * (y_range / x_range), 1.8)
    fig_w   = panel_w * 3 + 1.8
    fig_h   = panel_h * 2 + 1.8   # extra bottom room for legend

    fig, axes = plt.subplots(2, 3, figsize=(fig_w, fig_h),
                             constrained_layout=False)
    fig.patch.set_facecolor("#1a1a1a")
    fig.subplots_adjust(left=0.04, right=0.97, top=0.94,
                        bottom=0.10, wspace=0.25, hspace=0.30)

    extent = [float(xi[0]), float(xi[-1]), float(yi[0]), float(yi[-1])]
    outline = [pe.withStroke(linewidth=2, foreground="black")]

    for ax, (vals, title) in zip(axes.flat, panels):
        ax.set_facecolor("#2a2a2a")

        # Background
        ax.imshow(rgb_hs, origin="lower", extent=extent,
                  aspect="equal", interpolation="bilinear")
        ax.set_xlim(xi[0], xi[-1])
        ax.set_ylim(yi[0], yi[-1])

        # Colour per panel: orange if this panel's value is NaN OR quality=WARNING
        valid     = np.isfinite(vals)
        ok_sel    = (t_quality == "OK") & valid
        warn_sel  = (t_quality == "WARNING") | ~valid

        # Target dots
        if ok_sel.any():
            ax.scatter(tx[ok_sel], ty[ok_sel],
                       s=40, c="#00e5ff", edgecolors="black",
                       linewidths=0.6, zorder=5)
        if warn_sel.any():
            ax.scatter(tx[warn_sel], ty[warn_sel],
                       s=40, c="#ff9800", edgecolors="black",
                       linewidths=0.6, zorder=5)

        # Value labels — only for valid points
        for x, y, v in zip(tx[valid], ty[valid], vals[valid]):
            ax.annotate(f"{v:.2f}", xy=(x, y),
                        xytext=(5, 5), textcoords="offset points",
                        fontsize=7, color="white", fontweight="bold",
                        path_effects=outline, zorder=6)

        ax.set_title(title, color="white", fontsize=9, pad=4)
        ax.tick_params(colors="gray", labelsize=7)
        for spine in ax.spines.values():
            spine.set_edgecolor("#555555")

        cbar = fig.colorbar(sm, ax=ax, fraction=0.03, pad=0.02)
        cbar.ax.tick_params(colors="gray", labelsize=6)
        cbar.set_label("nT", color="gray", fontsize=6)

    # ── Legend (bottom centre) ─────────────────────────────────────────────
    legend_handles = [
        Line2D([0], [0], marker="o", color="none", markersize=9,
               markerfacecolor="#00e5ff", markeredgecolor="black",
               markeredgewidth=0.6, label="OK — depth ≤ 2 m"),
        Line2D([0], [0], marker="o", color="none", markersize=9,
               markerfacecolor="#ff9800", markeredgecolor="black",
               markeredgewidth=0.6, label="WARNING / N/A — depth > 2 m or not estimated"),
    ]
    fig.legend(handles=legend_handles, loc="lower center", ncol=2,
               frameon=True, framealpha=0.25, edgecolor="#555555",
               facecolor="#1a1a1a", fontsize=9,
               labelcolor="white", bbox_to_anchor=(0.5, 0.01))

    fig.suptitle("Depth & Distance Estimation", color="white", fontsize=12)
    plt.savefig(png_path, dpi=150, bbox_inches="tight",
                facecolor=fig.get_facecolor())
    plt.close(fig)
    print(f"Visualisation saved to {png_path}")


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
        description="Single-sensor magnetic depth estimation (2D grid Analytical Signal method)."
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
    parser.add_argument("--cell-size", type=float, default=None,
                        help="Grid cell size in metres (default: auto from data)")
    parser.add_argument("--blanking-distance", type=float, default=None,
                        help="Blanking distance in metres (default: 10 × cell size)")
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
    _SCRIPT_OUTPUT_COLS = {
        "TMI_anom", "IGRF_field", "Analytic_Signal",
        "Estimated_Distance_A", "Estimated_Depth_A",
        "Estimated_Distance_B", "Estimated_Depth_B",
        "Estimated_Distance", "Estimated_Depth",
        "Quality_Flag", "Error_Percent",
    }
    original_cols = [c for c in data.columns if c not in _SCRIPT_OUTPUT_COLS]
    data = data[original_cols]  # drop any stale script-output columns from previous runs

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
    # Along-track x & local XY
    # ------------------------------------------------------------------
    lats = pd.to_numeric(data["Latitude"], errors="coerce").values
    lons = pd.to_numeric(data["Longitude"], errors="coerce").values
    x_track = build_along_track(lats, lons)
    x_m, y_m, lat0, lon0 = latlon_to_local_xy(lats, lons)

    # ------------------------------------------------------------------
    # Flight lines
    # ------------------------------------------------------------------
    lines = split_into_lines(ts_series)
    print(f"Detected {len(lines)} flight line(s)")

    # ------------------------------------------------------------------
    # Per-line trend removal (before gridding)
    # ------------------------------------------------------------------
    n_rows = len(data)
    tmi_detrended = np.full(n_rows, np.nan)
    poor_trend_fit = np.zeros(n_rows, dtype=bool)

    for line_idx in lines:
        line_marks = marks[line_idx]
        line_x = x_track[line_idx]
        line_tmi = tmi_anom[line_idx]
        line_tmi_dt, is_poor = remove_trend(line_tmi, line_x, line_marks)
        tmi_detrended[line_idx] = line_tmi_dt
        if is_poor:
            poor_trend_fit[line_idx] = True

    # ------------------------------------------------------------------
    # Build 2D grid and compute AS
    # ------------------------------------------------------------------
    cell_size = args.cell_size or DEFAULT_CELL_SIZE_M or estimate_cell_size(x_m, y_m, lines)
    print(f"Grid cell size: {cell_size:.2f} m")

    grid, xi, yi = build_2d_grid(x_m, y_m, tmi_detrended, cell_size,
                                 blanking_distance=args.blanking_distance)
    print(f"Grid dimensions: {grid.shape[0]} rows × {grid.shape[1]} cols")

    as_grid = compute_as_2d(grid, cell_size, cell_size)

    # Sample AS grid at original data positions
    as_at_points = sample_grid_at_points(as_grid, xi, yi, x_m, y_m)
    # Keep NaN where the AS grid had no data — nanmean in aggregate_spatial
    # handles these correctly instead of diluting bins with zeros.

    # ------------------------------------------------------------------
    # Output arrays
    # ------------------------------------------------------------------
    out_dist_a = np.full(n_rows, np.nan)
    out_depth_a = np.full(n_rows, np.nan)
    out_dist_b = np.full(n_rows, np.nan)
    out_depth_b = np.full(n_rows, np.nan)
    out_dist_mean = np.full(n_rows, np.nan)
    out_depth_mean = np.full(n_rows, np.nan)
    out_quality = np.full(n_rows, "", dtype=object)
    out_error_pct = np.full(n_rows, np.nan)

    anomaly_id = 0

    # ------------------------------------------------------------------
    # Per-line anomaly processing (using grid-sampled AS)
    # ------------------------------------------------------------------
    for line_idx in lines:
        line_marks = marks[line_idx]
        line_x = x_track[line_idx]
        line_tmi = tmi_detrended[line_idx]
        line_agl = agl_values[line_idx]
        line_lat = lats[line_idx]
        line_lon = lons[line_idx]
        line_ts = ts_series.values[line_idx]
        line_as = as_at_points[line_idx]

        if not np.any(line_marks == 1):
            continue

        # Spatial aggregation
        agg_x, agg_tmi, agg_marks, agg_agl, agg_lat, agg_lon, agg_ts = aggregate_spatial(
            line_x, line_tmi, line_marks,
            line_agl, line_lat, line_lon, line_ts,
        )

        # Aggregate the grid-sampled AS values the same way.
        # aggregate_spatial returns (x, values, marks, agl, lat, lon, ts)
        # — AS is passed as the 'tmi' parameter, so it comes back as the
        # second element.
        _, agg_as, _, _, _, _, _ = aggregate_spatial(
            line_x, line_as, line_marks,
            line_agl, line_lat, line_lon, line_ts,
        )

        # Find contiguous marked groups
        is_marked = (agg_marks == 1).astype(np.int8)
        starts = np.where(np.diff(np.concatenate([[0], is_marked])) == 1)[0]
        ends = np.where(np.diff(np.concatenate([is_marked, [0]])) == -1)[0]

        for g_start, g_end in zip(starts, ends):
            anomaly_id += 1
            _process_anomaly_group(
                g_start, g_end, anomaly_id,
                agg_x, agg_tmi, agg_marks, agg_agl, agg_lat, agg_lon, agg_ts,
                agg_as,
                line_idx, line_x, line_marks,
                poor_trend_fit,
                out_dist_a, out_depth_a, out_dist_b, out_depth_b,
                out_dist_mean, out_depth_mean, out_quality, out_error_pct,
            )

    # ------------------------------------------------------------------
    # Save
    # ------------------------------------------------------------------
    def _as_col(arr):
        out = arr.astype(object)
        out[np.isnan(arr)] = ""
        return out

    data["Estimated_Distance_A"] = _as_col(out_dist_a)
    data["Estimated_Depth_A"]    = _as_col(out_depth_a)
    data["Estimated_Distance_B"] = _as_col(out_dist_b)
    data["Estimated_Depth_B"]    = _as_col(out_depth_b)
    data["Estimated_Distance"]   = _as_col(out_dist_mean)
    data["Estimated_Depth"]      = _as_col(out_depth_mean)

    print(f"Writing result to {input_path}")
    data.to_csv(input_path, index=False, sep=CSV_SEPARATOR)

    targets_path = _build_targets_path(input_path, args.output_dir)
    target_mask = marks == 1
    targets_df = data.loc[target_mask, original_cols + [
        "Estimated_Distance_A", "Estimated_Depth_A",
        "Estimated_Distance_B", "Estimated_Depth_B",
        "Estimated_Distance",   "Estimated_Depth",
    ]].copy()
    targets_df["Quality_Flag"] = out_quality[target_mask]
    targets_df["Error_Percent"] = _as_col(out_error_pct)[target_mask]
    print(f"Writing targets to {targets_path}")
    targets_df.to_csv(targets_path, index=False, sep=CSV_SEPARATOR)

    # ── Visualisation ──────────────────────────────────────────────────────
    png_path = targets_path.replace("-targets-as.csv", "-depth-map.png")
    visualize_results(
        grid, xi, yi,
        x_m, y_m, marks,
        out_dist_a, out_depth_a,
        out_dist_b, out_depth_b,
        out_dist_mean, out_depth_mean,
        out_quality,
        png_path,
    )

    print(f"Done. {anomaly_id} anomaly group(s) processed.")


if __name__ == "__main__":
    main()