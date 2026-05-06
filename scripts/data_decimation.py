import sys
import argparse

import numpy as np
import pandas as pd
from scipy.interpolate import Akima1DInterpolator

DATA_VALID_COLUMN = "Data valid"


def filter_invalid_rows(data):
    if DATA_VALID_COLUMN in data.columns:
        data = data[data[DATA_VALID_COLUMN] != 0].reset_index(drop=True)
    return data


def classify_columns(data):
    numeric_cols = data.select_dtypes(include="number").columns.tolist()
    text_cols = [col for col in data.columns if col not in numeric_cols]
    return numeric_cols, text_cols


def detect_angular_columns(data, numeric_cols):
    angular = set()
    for col in numeric_cols:
        s = data[col].dropna()
        if len(s) < 2:
            continue
        if s.min() < 0 or s.max() > 360:
            continue
        if np.abs(np.diff(s.to_numpy())).max() > 180:
            angular.add(col)
    return angular


def unwrap_degrees(values):
    result = values.copy()
    mask = np.isfinite(result)
    if mask.sum() >= 2:
        result[mask] = np.unwrap(result[mask], period=360)
    return result


def _decimate_windowed(data, factor, numeric_cols, text_cols, angular_cols, agg_name):
    n = len(data)
    groups = np.arange(n) // factor

    # Center index of each window — used for text columns
    starts = np.arange(0, n, factor)
    centers = starts + (np.minimum(starts + factor, n) - starts) // 2

    result = {}

    # Text columns: value from center sample of each window
    for col in text_cols:
        result[col] = data[col].to_numpy()[centers]

    # Non-angular numeric columns: vectorized groupby (pandas skips NaN by default)
    regular_cols = [col for col in numeric_cols if col not in angular_cols]
    if regular_cols:
        grouped = data[regular_cols].groupby(groups, sort=False)
        decimated = grouped.mean() if agg_name == "mean" else grouped.median()
        for col in regular_cols:
            result[col] = decimated[col].to_numpy()

    # Angular columns: unwrap per window, then aggregate
    for col in angular_cols:
        col_values = np.empty(len(centers))
        for i, start in enumerate(starts):
            end = min(start + factor, n)
            values = unwrap_degrees(data[col].to_numpy(dtype=float)[start:end])
            agg = np.nanmean(values) if agg_name == "mean" else np.nanmedian(values)
            col_values[i] = agg % 360
        result[col] = col_values

    return pd.DataFrame(result, columns=data.columns)


def decimate_mean(data, factor, numeric_cols, text_cols, angular_cols):
    return _decimate_windowed(data, factor, numeric_cols, text_cols, angular_cols, "mean")


def decimate_median(data, factor, numeric_cols, text_cols, angular_cols):
    return _decimate_windowed(data, factor, numeric_cols, text_cols, angular_cols, "median")


def decimate_interpolated(data, factor, numeric_cols, text_cols, angular_cols):
    n = len(data)
    original_t = np.arange(n, dtype=float)
    median_dt = float(np.median(np.diff(original_t))) if n > 1 else 1.0
    step = factor * median_dt
    output_t = np.arange(factor // 2, n, step)

    center_indices = np.clip(np.round(output_t).astype(int), 0, n - 1)
    result = {}
    for col in text_cols:
        result[col] = data[col].iloc[center_indices].to_numpy()
    for col in numeric_cols:
        values = data[col].to_numpy(dtype=float)
        if col in angular_cols:
            values = unwrap_degrees(values)
        finite_mask = np.isfinite(values)
        if finite_mask.sum() >= 3:
            akima = Akima1DInterpolator(original_t[finite_mask], values[finite_mask])
            col_values = akima(output_t)
        else:
            col_values = np.full(len(output_t), np.nan)
        result[col] = col_values % 360 if col in angular_cols else col_values

    return pd.DataFrame(result, columns=data.columns)


ALGORITHMS = {
    "MEAN": decimate_mean,
    "MEDIAN": decimate_median,
    "INTERPOLATED": decimate_interpolated,
}


def main():
    parser = argparse.ArgumentParser(
        description="Decimate data measurements by reducing rows using a selected algorithm."
    )
    parser.add_argument("file_path", help="File path")
    parser.add_argument(
        "--factor",
        type=int,
        default=10,
        help="Decimation factor: keep one representative per N measurements (default: 10, range: 2..500)",
    )
    parser.add_argument(
        "--algorithm",
        default="MEDIAN",
        help="Decimation algorithm: MEAN, MEDIAN, or INTERPOLATED (default: MEDIAN)",
    )
    args = parser.parse_args()

    if args.algorithm not in ALGORITHMS:
        print(f"Error: Unknown algorithm '{args.algorithm}'. Choose from: {', '.join(ALGORITHMS)}")
        sys.exit(1)

    print(f"Reading file {args.file_path}")
    data = pd.read_csv(args.file_path)
    data = filter_invalid_rows(data)

    numeric_cols, text_cols = classify_columns(data)
    angular_cols = detect_angular_columns(data, numeric_cols)
    if angular_cols:
        print(f"Detected angular columns: {sorted(angular_cols)}")

    original_count = len(data)
    print(f"Decimating with factor {args.factor} using {args.algorithm}")

    decimated = ALGORITHMS[args.algorithm](data, args.factor, numeric_cols, text_cols, angular_cols)

    print(f"Rows: {original_count} → {len(decimated)}")
    print(f"Writing result to {args.file_path}")
    decimated.to_csv(args.file_path, index=False)
    print("Done")


if __name__ == "__main__":
    main()
