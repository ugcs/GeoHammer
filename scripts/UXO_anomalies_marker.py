import argparse
import json
from typing import Dict, List

import numpy as np
import pandas as pd
from scipy.spatial import cKDTree


def markExtremes(
    df: pd.DataFrame,
    column: str,
    threshold: float,
    window_width: int,
    include_partial: bool = True,
    equality_tolerance: float = 0.0,
) -> Dict[int, float]:
    """
    Return a dict mapping positional row index -> amplitude (signed deviation from window - window_mean)
    for samples that are equal to the min or max within their non-overlapping
    window of size W and whose |value - window_mean| exceeds 'threshold'.

    - Windows are non-overlapping chunks of length W (not centered).
    - If include_partial is True, process the final shorter chunk as well.
    - equality_tolerance can be used to treat values within +/- equality_tolerance as equal to min/max.
    """
    if column not in df.columns:
        raise ValueError(f"column '{column}' not found")

    W = int(window_width)
    if W <= 0:
        raise ValueError("window_width must be > 0")

    column_data = pd.to_numeric(df[column], errors="coerce").to_numpy()
    n = column_data.size
    mapping: Dict[int, float] = {}

    if n == 0:
        return mapping

    length_chunks = n // W
    length = length_chunks * W  # number of elements covered by full chunks

    if length_chunks:
        chunks = column_data[:length].reshape(length_chunks, W)

        # Extremum masks considering tolerance
        mins = np.nanmin(chunks, axis=1)
        maxs = np.nanmax(chunks, axis=1)
        if equality_tolerance == 0.0:
            mask_min = (chunks == mins[:, None])
            mask_max = (chunks == maxs[:, None])
        else:
            mask_min = (np.abs(chunks - mins[:, None]) <= equality_tolerance)
            mask_max = (np.abs(chunks - maxs[:, None]) <= equality_tolerance)
        is_extreme = mask_min | mask_max

        # Deviation from window mean (signed)
        means = np.nanmean(chunks, axis=1)
        dev = chunks - means[:, None]

        # Flag if |dev| > threshold
        mask = is_extreme & (np.abs(dev) > threshold)
        flat_mask = mask.ravel()
        if flat_mask.any():
            idx = np.nonzero(flat_mask)[0]
            dvals = dev.ravel()[flat_mask]
            mapping.update({int(i): float(v) for i, v in zip(idx, dvals)})

    # Optional tail chunk (use positional indices length..n-1)
    if include_partial and length < n:
        tail = column_data[length:]
        if tail.size:
            min_tail = np.nanmin(tail)
            max_tail = np.nanmax(tail)
            if not (np.isnan(min_tail) or np.isnan(max_tail)):
                if equality_tolerance == 0.0:
                    mask_tail_min = (tail == min_tail)
                    mask_tail_max = (tail == max_tail)
                else:
                    mask_tail_min = (np.abs(tail - min_tail) <= equality_tolerance)
                    mask_tail_max = (np.abs(tail - max_tail) <= equality_tolerance)

                mask_tail = mask_tail_min | mask_tail_max

                tail_mean = np.nanmean(tail)
                dev_tail = tail - tail_mean
                mask_tail &= (np.abs(dev_tail) > threshold)

                tail_idx = np.nonzero(mask_tail)[0]
                for j in tail_idx:
                    mapping[int(length + j)] = float(dev_tail[j])
    return mapping

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
    Cluster points in 2D (meters) using KD-tree + BFS.
    - df: source DataFrame with lat/lon columns
    - indices: mapping {positional_row_index -> range_value}
    - radius: clustering radius in meters (Web Mercator)
    - latitude_column_name: name of latitude column in df
    - longitude_column_name: name of longitude column in df
    Returns: {positional_row_index -> range_value} with one winner per group (max abs(range)).
    """
    if not indices:
        return {}
    if radius < 0:
        raise ValueError("radius must be non-negative")
    if latitude_column_name not in df.columns or longitude_column_name not in df.columns:
        raise ValueError("lat/lon columns not found in DataFrame")

    # Work on a stable order of candidate positional indices
    idx_list = np.array(sorted(indices.keys()), dtype=int)

    # Extract lat/lon using iloc because idx_list are positions
    lat = pd.to_numeric(df.iloc[idx_list][latitude_column_name], errors="coerce").to_numpy()
    lon = pd.to_numeric(df.iloc[idx_list][longitude_column_name], errors="coerce").to_numpy()
    valid = np.isfinite(lat) & np.isfinite(lon)
    if not np.any(valid):
        return {}

    idx_list = idx_list[valid]
    lat = lat[valid]
    lon = lon[valid]
    vals = np.array([indices[i] for i in idx_list], dtype=float)

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

    # BFS over neighbor graph defined by radius
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
        winner_df_pos = int(idx_list[winner_local])  # positional index
        result[winner_df_pos] = float(vals[winner_local])

    return result

def main():
    parser = argparse.ArgumentParser(description="Find extrema and return index->range mapping.")
    parser.add_argument("file_path", help="Input CSV path")
    parser.add_argument("-c", "--column", required=True, help="Column to analyze (e.g., TMI or TMI_anomaly)")
    parser.add_argument("-t", "--threshold", required=True, type=float, help="Threshold (range)")
    parser.add_argument("-w", "--window", required=True, type=int, help="Window (samples)")
    parser.add_argument("-r", "--radius", required=True, type=float, help="Clustering radius (meters)")
    parser.add_argument("--clear-marks", action="store_true", default=False, help="Clear all previous Mark values before writing new marks")
    parser.add_argument("-e", "--tolerance", type=float, default=0.0, help="Equality tolerance for min/max")
    parser.add_argument("--lat-col", default="Latitude", help="Latitude column name")
    parser.add_argument("--lon-col", default="Longitude", help="Longitude column name")
    args = parser.parse_args()

    # Enforce positional indexing semantics throughout
    df = pd.read_csv(args.file_path).reset_index(drop=True)

    index_to_range = markExtremes(
        df,
        column=args.column,
        threshold=args.threshold,
        window_width=args.window,
        equality_tolerance=args.tolerance,
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
