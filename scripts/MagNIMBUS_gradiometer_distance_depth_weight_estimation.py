import sys
import os
import argparse
import pandas as pd
import numpy as np
from script_utils import normalize_input_stem


def check_column_exists(data, column):
    if column not in data.columns:
        print(f"Error: Column '{column}' not found in input data")
        sys.exit(1)

def check_column_numeric(data, column):
    if not pd.api.types.is_numeric_dtype(data[column]):
        print(f"Error: Column '{column}' must be numeric")
        sys.exit(1)


def process_gradiometer(data, mark_indices, tmi_lower_col, tmi_upper_col, altitude_col,
                        sensor_separation, altimeter_lower_offset):
    """
    Process marked points using gradiometer mode (two sensors).

    Algorithm:
    1. Estimate per-sensor background as median for upper column (less affected by near-surface anomalies).
    2. anomaly_lower = TMI_lower - background
    3. anomaly_upper = TMI_upper - background
    4. distance_lower = sensor_separation / ((anomaly_lower / anomaly_upper)**(1/3) - 1)
    5. depth = distance_lower - AGL + altimeter_lower_offset
    6. weight (kg) = min(|anomaly_lower| * distance_ft^3 / 1000 * 0.453592, |anomaly_lower| / distance_ft^1.5)
    """
    background = data[tmi_upper_col].median()

    distances = []
    depths = []
    weights = []

    has_altitude = altitude_col in data.columns

    for idx in mark_indices:
        tmi_lower = data[tmi_lower_col].iloc[idx]
        tmi_upper = data[tmi_upper_col].iloc[idx]
        altitude_agl = data[altitude_col].iloc[idx] if has_altitude else np.nan

        if pd.isna(tmi_lower) or pd.isna(tmi_upper):
            print(f"Warning: Missing TMI data at index {idx}, skipping")
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        # Calculate anomaly values (subtract background field)
        anomaly_lower = tmi_lower - background
        anomaly_upper = tmi_upper - background

        if anomaly_upper == 0:
            print(f"Warning: Zero anomaly at upper sensor at index {idx}, skipping")
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        # Calculate anomaly ratio
        ratio = anomaly_lower / anomaly_upper

        if ratio < 0:
            print(f"Warning: Opposite-sign anomalies at index {idx} (ratio={ratio:.4f}), skipping")
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        # Cube root of anomaly ratio
        cube_root = ratio ** (1/3)

        # Check for invalid denominator
        denominator = cube_root - 1
        if denominator == 0:
            print(f"Warning: Invalid denominator (ratio^(1/3) = 1) at index {idx}, skipping")
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        # Distance to lower sensor
        distance_lower = sensor_separation / denominator
        if distance_lower < 0:
            distance_lower = abs(distance_lower)
        distance_lower = round(distance_lower, 4)

        if distance_lower == 0:
            print(f"Warning: Distance rounded to zero at index {idx}, skipping")
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        # If altitude is missing, depth will be N/A
        if pd.isna(altitude_agl):
            depth = np.nan
        else:
            depth = distance_lower - altitude_agl + altimeter_lower_offset
            depth = round(depth, 4)
            if depth < 0:
                depth = 0

        distance_lower_ft = distance_lower / 0.3048
        LBS_TO_KG = 0.453592
        weight_dipole = abs(anomaly_lower) * distance_lower_ft ** 3 / 1000 * LBS_TO_KG
        weight_inv = abs(anomaly_lower) / distance_lower_ft ** 1.5
        weight = min(weight_dipole, weight_inv)
        weight = round(weight, 6)
        distances.append(distance_lower)
        depths.append(depth)
        weights.append(weight)

    return distances, depths, weights


def build_targets_path(input_path, output_dir):
    stem = os.path.splitext(os.path.basename(input_path))[0]
    stem = normalize_input_stem(stem)
    targets_name = stem + "-targets.csv"

    # If output-dir is empty, use the original file's directory
    if not output_dir or not output_dir.strip():
        output_dir = os.path.dirname(os.path.abspath(input_path))

    output_dir = output_dir.strip()
    output_dir = os.path.expanduser(os.path.expandvars(output_dir))

    os.makedirs(output_dir, exist_ok=True)
    return os.path.join(output_dir, targets_name)

def main():
    parser = argparse.ArgumentParser(
        description="Estimates distance and depth from MagNIMBUS gradiometer data (two sensors)."
    )

    parser.add_argument("file_path", help='File path')
    parser.add_argument("--lower-sensor-column", default="TMI_LPF", help="Lower sensor column (default: TMI_LPF)")
    parser.add_argument("--upper-sensor-column", default="TMI_S_LPF", help="Upper sensor column (default: TMI_S_LPF)")
    parser.add_argument("--altitude-column", default="Altitude AGL", help="Altitude AGL column (default: Altitude_AGL)")
    parser.add_argument("--sensor-separation", type=float, default=1.5, help="Sensor separation in meters (default: 1.5)")
    parser.add_argument("--altimeter-lower-offset", type=float, default=0.5,
                        help="Distance between altimeter and lower sensor in meters (default: 0.5)")
    parser.add_argument("--output-dir", dest="output_dir", type=str, default="",
                        help="Output directory for targets file (default: same as input file)")
    args = parser.parse_args()

    input_path = args.file_path
    lower_column = args.lower_sensor_column
    upper_column = args.upper_sensor_column
    altitude_column = args.altitude_column

    print(f"Reading file {input_path}")
    data = pd.read_csv(input_path)

    # Validate required columns
    check_column_exists(data, lower_column)
    check_column_numeric(data, lower_column)
    check_column_exists(data, upper_column)
    check_column_numeric(data, upper_column)

    # Altitude column is optional - if missing, depth will be N/A
    if altitude_column not in data.columns:
        print("Altitude AGL is not available in the data file, the Estimated Depth column contains the distance from the bottom sensor.")

    if "Mark" not in data.columns:
        print("Error: Column 'Mark' not found in input data")
        sys.exit(1)

    # Check for marked points
    marks = pd.to_numeric(data["Mark"], errors="coerce").fillna(0)
    mark_indices = marks[marks == 1].index.tolist()

    if len(mark_indices) == 0:
        print("No marked points found (Mark = 1). Nothing to do.")
        sys.exit(0)

    print(f"Found {len(mark_indices)} marked points")

    # Process gradiometer data
    distances, depths, weights = process_gradiometer(
        data, mark_indices, lower_column, upper_column, altitude_column,
        args.sensor_separation, args.altimeter_lower_offset
    )

    # Save results - add columns with empty string for non-marked rows
    data["Estimated_Distance"] = ""
    data["Estimated_Depth"] = ""
    data["Estimated_Weight"] = ""

    for i, idx in enumerate(mark_indices):
        if not np.isnan(distances[i]):
            data.at[idx, "Estimated_Distance"] = distances[i]
        if not np.isnan(depths[i]):
            data.at[idx, "Estimated_Depth"] = depths[i]
        if not np.isnan(weights[i]):
            data.at[idx, "Estimated_Weight"] = weights[i]

    # Overwrite CSV
    output_path = input_path
    print(f"Writing result to {output_path}")
    data.to_csv(output_path, index=False, sep=',')

    # Create targets file with only marked rows
    targets_path = build_targets_path(input_path, args.output_dir)
    targets_data = data.loc[mark_indices]
    print(f"Writing targets file to {targets_path}")
    targets_data.to_csv(targets_path, index=False, sep=',')

    print("Done")


if __name__ == "__main__":
    main()
