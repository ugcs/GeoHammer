import argparse
import json
from typing import Dict

import numpy as np
import pandas as pd
from scipy.spatial import cKDTree


def markExtremes(
    df: pd.DataFrame,
    column: str,
    threshold: float,
    window_width: int,
    include_partial: bool = False,
    equality_tolerance: float = 0.0,
) -> Dict[int, float]:
    """
    Return a dict mapping *positional row index* -> (maxW - minW) for samples that are
    equal to the min or max within their non-overlapping window of size W,
    and whose window range exceeds 'threshold'.

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

    # Full chunks
    if length_chunks:
        chunks = column_data[:length].reshape(length_chunks, W)
        mins = np.nanmin(chunks, axis=1)
        maxs = np.nanmax(chunks, axis=1)
        ranges = maxs - mins
        keep = ranges > threshold

        if equality_tolerance == 0.0:
            is_extreme = (chunks == mins[:, None]) | (chunks == maxs[:, None])
        else:
            is_extreme = (np.abs(chunks - mins[:, None]) <= equality_tolerance) | (np.abs(chunks - maxs[:, None]) <= equality_tolerance)

        mask = is_extreme & keep[:, None]
        flat_mask = mask.ravel()
        if flat_mask.any():
            idx = np.nonzero(flat_mask)[0]  # positional indices within [:length]
            rvals = np.repeat(ranges, W)[flat_mask]
            mapping.update({int(i): float(rv) for i, rv in zip(idx, rvals)})

    # Optional tail chunk (use positional indices length..n-1)
    if include_partial and length < n:
        tail = column_data[length:]
        if tail.size:
            min_tail = np.nanmin(tail)
            max_tail = np.nanmax(tail)
            if not (np.isnan(min_tail) or np.isnan(max_tail)):
                win_range = max_tail - min_tail
                if win_range > threshold:
                    if equality_tolerance == 0.0:
                        mask_tail = (tail == min_tail) | (tail == max_tail)
                    else:
                        mask_tail = (np.abs(tail - min_tail) <= equality_tolerance) | (np.abs(tail - max_tail) <= equality_tolerance)
                    tail_idx = np.nonzero(mask_tail)[0]
                    for j in tail_idx:
                        mapping[int(length + j)] = float(win_range)
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
            for v in tree.query_ball_point(pts[u], r=radius):
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
    print(f"Mapping extremes finished: {len(index_to_range)} candidates")

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

    # Prepare / reset Mark column each run (nullable Int8)
    df["Mark"] = pd.Series(pd.NA, index=df.index, dtype="Int8")
    if clustered_map:
        marked_positions = np.fromiter(clustered_map.keys(), dtype=int)
        df.iloc[marked_positions, df.columns.get_loc("Mark")] = 1

    # Save to same file
    df.to_csv(args.file_path, index=False)
    print(f"Wrote output with markers to: {args.file_path}")


if __name__ == "__main__":
    main()
