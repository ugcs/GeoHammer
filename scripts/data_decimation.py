import sys
import argparse

import numpy as np
import pandas as pd


def check_column_exists(data, column):
    if column not in data.columns:
        print(f"Error: Column '{column}' not found in input data")
        sys.exit(1)


def check_column_numeric(data, column):
    if not pd.api.types.is_numeric_dtype(data[column]):
        print(f"Error: Column '{column}' must be numeric")
        sys.exit(1)


def decimate_median(data, column, factor):
    n = len(data)
    groups = np.arange(n) // factor
    decimated = (
        data.groupby(groups, sort=False)
        .agg({col: "median" if col == column else "first" for col in data.columns})
        .reset_index(drop=True)
    )
    return decimated


def decimate_mean(data, column, factor):
    n = len(data)
    groups = np.arange(n) // factor
    decimated = (
        data.groupby(groups, sort=False)
        .agg({col: "mean" if col == column else "first" for col in data.columns})
        .reset_index(drop=True)
    )
    return decimated


def decimate_interpolated(data, column, factor):
    n = len(data)
    # Keep every factor-th row, then interpolate the target column back to original length
    kept_indices = np.arange(0, n, factor)
    original_indices = np.arange(n)
    interpolated_values = np.interp(original_indices, kept_indices, data[column].iloc[kept_indices].to_numpy())
    result = data.copy()
    result[column] = interpolated_values
    # Thin to one row per block (first row), replace target column value with block representative
    groups = np.arange(n) // factor
    representatives = (
        pd.Series(interpolated_values, name=column)
        .groupby(groups)
        .first()
        .reset_index(drop=True)
    )
    first_rows = (
        data.groupby(groups, sort=False)
        .first()
        .reset_index(drop=True)
    )
    first_rows[column] = representatives
    return first_rows


ALGORITHMS = {
    "MEDIAN": decimate_median,
    "MEAN": decimate_mean,
    "INTERPOLATED": decimate_interpolated,
}


def main():
    parser = argparse.ArgumentParser(
        description="Decimate data measurements by reducing rows using a selected algorithm."
    )
    parser.add_argument("file_path", help="File path")
    parser.add_argument("--column", required=True, help="Column to decimate")
    parser.add_argument(
        "--factor",
        type=int,
        default=10,
        help="Decimation factor: keep one representative per N measurements (default: 10, range: 2..500)",
    )
    parser.add_argument(
        "--algorithm",
        default="MEDIAN",
        choices=list(ALGORITHMS.keys()),
        help="Decimation algorithm: MEDIAN, MEAN, or INTERPOLATED (default: MEDIAN)",
    )
    args = parser.parse_args()

    if not (2 <= args.factor <= 500):
        print(f"Error: Decimation factor must be between 2 and 500, got {args.factor}")
        sys.exit(1)

    print(f"Reading file {args.file_path}")
    data = pd.read_csv(args.file_path)

    check_column_exists(data, args.column)
    check_column_numeric(data, args.column)

    original_count = len(data)
    print(f"Decimating column '{args.column}' with factor {args.factor} using {args.algorithm}")

    decimated = ALGORITHMS[args.algorithm](data, args.column, args.factor)

    print(f"Rows: {original_count} → {len(decimated)}")
    print(f"Writing result to {args.file_path}")
    decimated.to_csv(args.file_path, index=False)
    print("Done")


if __name__ == "__main__":
    main()
