import argparse
from typing import Dict

import numpy as np
import pandas as pd
from scipy.spatial import cKDTree
from scipy.signal import find_peaks


def markExtremes(
    df: pd.DataFrame,
    column: str,
    threshold: float,
    window_width: int
) -> Dict[int, float]:
    """
    Return a dict mapping positional row index -> amplitude (signed deviation from window median)
    for samples that are equal to the min or max within their centered sliding window
    of size W and whose |value - window_median| exceeds 'threshold'.

    For each index i, the window is [i - W/2, i + W/2], with proper boundary handling.
    """
    if column not in df.columns:
        raise ValueError(f"column '{column}' not found")

    W = int(window_width)
    if W <= 0:
        raise ValueError("window_width must be > 0")

    series = pd.to_numeric(df[column], errors="coerce")

    roll_median = series.rolling(W, center=True, min_periods=1).median()
    deviation = series - roll_median

    peaks_max, _ = find_peaks(series.fillna(-np.inf), distance=1, prominence=threshold)
    peaks_min, _ = find_peaks(-series.fillna(np.inf), distance=1, prominence=threshold)

    all_extrema = np.concatenate([peaks_max, peaks_min])
    mask = deviation.iloc[all_extrema].abs() > threshold
    
    return deviation.iloc[all_extrema[mask]].to_dict()

def _latlon_to_webmercator(lat: np.ndarray, lon: np.ndarray) -> np.ndarray:
    """Convert WGS84 lat/lon (degrees) to Web Mercator X,Y (meters)."""
    R = 6378137.0
    lat = np.clip(lat, -85.05112878, 85.05112878)
    lat_rad = np.radians(lat)
    lon_rad = np.radians(lon)
    x = R * lon_rad
    y = R * np.log(np.tan(np.pi / 4.0 + lat_rad / 2.0))
    return np.column_stack((x, y))

def cluster_by_radius_2d(
        df: pd.DataFrame,
        indices: Dict[int, float],
        radius: float,
        latitude_column_name: str = "Latitude",
        longitude_column_name: str = "Longitude",
) -> Dict[int, float]:
    """
    Cluster points in 2D (meters) using KD-tree + DFS.
    - df: source DataFrame with lat/lon columns
    - indices: mapping {positional_row_index -> amplitude (signed deviation from window median)}
    - radius: clustering radius in meters (Web Mercator)
    - latitude_column_name: name of latitude column in df
    - longitude_column_name: name of longitude column in df
    Returns: {positional_row_index -> amplitude (signed deviation from window median)} with one winner per group (max abs(amplitude)).
    """
    if not indices:
        return {}
    if radius < 0:
        raise ValueError("radius must be non-negative")
    if latitude_column_name not in df.columns or longitude_column_name not in df.columns:
        raise ValueError("lat/lon columns not found in DataFrame")

    # Work on a stable order of candidate positional indices
    index_list = np.array(sorted(indices.keys()), dtype=int)

    # Extract lat/lon using iloc because index_list are positions
    lat = pd.to_numeric(df.iloc[index_list][latitude_column_name], errors="coerce").to_numpy()
    lon = pd.to_numeric(df.iloc[index_list][longitude_column_name], errors="coerce").to_numpy()
    valid = np.isfinite(lat) & np.isfinite(lon)
    if not np.any(valid):
        return {}

    index_list = index_list[valid]
    lat = lat[valid]
    lon = lon[valid]
    vals = np.array([indices[i] for i in index_list], dtype=float)

    # Project to meters and build KD-tree
    pts = _latlon_to_webmercator(lat, lon)
    tree = cKDTree(pts)

    # Adjust radius for Mercator scale at mean latitude
    mean_lat = float(np.nanmedian(lat))
    scale = 1.0 / np.cos(np.deg2rad(mean_lat))
    radius_eff = radius * scale

    n = pts.shape[0]
    visited = np.zeros(n, dtype=bool)
    result: Dict[int, float] = {}

    # DFS over neighbor graph defined by radius
    for i in range(n):
        if visited[i]:
            continue
        queue = [i]
        visited[i] = True
        component = [i]

        while queue:
            u = queue.pop()
            for v in tree.query_ball_point(pts[u], r=radius_eff):
                if not visited[v]:
                    visited[v] = True
                    queue.append(v)
                    component.append(v)

        # pick the index with maximum absolute range
        comp_vals = vals[component]
        winner_local = component[int(np.argmax(np.abs(comp_vals)))]
        winner_df_pos = int(index_list[winner_local])  # positional index
        result[winner_df_pos] = float(vals[winner_local])

    return result

def main():
    parser = argparse.ArgumentParser(description="Find extrema and return index->range mapping.")
    parser.add_argument("file_path", help="Input CSV path")
    parser.add_argument("-c", "--column", required=True, help="Column to analyze (e.g., TMI or TMI_RM)")
    parser.add_argument("-t", "--threshold", required=True, type=float, help="Threshold (range)")
    parser.add_argument("-w", "--window", required=True, type=int, help="Window (samples)")
    parser.add_argument("-r", "--radius", required=True, type=float, help="Clustering radius (meters)")
    parser.add_argument("--clear-marks", action="store_true", default=False, help="Clear all previous Mark values before writing new marks")
    parser.add_argument("--lat-col", default="Latitude", help="Latitude column name")
    parser.add_argument("--lon-col", default="Longitude", help="Longitude column name")
    args = parser.parse_args()

    # Enforce positional indexing semantics throughout
    df = pd.read_csv(args.file_path).reset_index(drop=True)

    index_to_range = markExtremes(
        df,
        column=args.column,
        threshold=args.threshold,
        window_width=args.window
    )

    clustered_map = cluster_by_radius_2d(
        df,
        index_to_range,
        radius=args.radius,
        latitude_column_name=args.lat_col,
        longitude_column_name=args.lon_col,
    )
    print(f"Found anomalies after clustering: {len(clustered_map)} winners")

    # Add amplitude column aligned by positional index
    amp_series = pd.Series(clustered_map, dtype=float)
    df["Marked_Anomaly_Amplitude"] = amp_series.reindex(range(len(df)), fill_value=np.nan).to_numpy()
   

    #1 Read Mark column if exists
    current_marks = None
    if "Mark" in df.columns:
        current_marks = pd.to_numeric(df["Mark"], errors="coerce").fillna(0).astype(np.int8).to_numpy()
    else:
        current_marks = np.zeros(len(df), dtype=np.int8)

    #2 Compute new-mark value (0 or 1)
    new_marks = None
    if args.clear_marks:
        new_marks = np.zeros(len(df), dtype=np.int8)
    else:
        new_marks = current_marks.copy()

    if clustered_map:
        for pos in clustered_map.keys():
            new_marks[pos] = 1
    
    #3 Read auto-marked value (if no such column, use 0 for auto-marked)
    auto_marked = None
    if "Auto-Marked" in df.columns:
        auto_marked = df["Auto-Marked"].astype("Int8").to_numpy()
    else:
        auto_marked = np.zeros(len(df), dtype=np.int8)

    #4 Write auto-marked = mark xor new-mark xor auto-marked
    auto_marked = current_marks ^ new_marks ^ auto_marked
    df["Auto-Marked"] = pd.Series(auto_marked, index=df.index, dtype="Int8")

    #5 Write Mark = new-mark
    df["Mark"] = pd.Series(new_marks, index=df.index, dtype="Int8")

    # Save to same file
    df.to_csv(args.file_path, index=False)
    print(f"Wrote output with markers to: {args.file_path}")


if __name__ == "__main__":
    main()
