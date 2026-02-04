import sys
import os
import math
import argparse
import tempfile
import pandas as pd
import numpy as np


def str2bool(v):
    if isinstance(v, bool):
        return v
    if v is None:
        return False
    s = str(v).strip().lower()
    if s in ("1", "true", "t", "yes", "y", "on"):
        return True
    if s in ("0", "false", "f", "no", "n", "off", ""):
        return False
    raise argparse.ArgumentTypeError(f"Boolean value expected, got: {v}")


def build_targets_path(input_path, save_to, out_dir):
    stem = os.path.splitext(os.path.basename(input_path))[0]
    targets_name = stem + "-targets.csv"

    if save_to:
        out_dir = (out_dir or "").strip()
        out_dir = os.path.expanduser(os.path.expandvars(out_dir))

        if out_dir and os.path.splitext(out_dir)[1]:
            out_dir = os.path.dirname(out_dir)

        if not out_dir:
            out_dir = os.path.dirname(os.path.abspath(input_path))

        os.makedirs(out_dir, exist_ok=True)
        targets_path = os.path.join(out_dir, targets_name)

        try:
            testfile = os.path.join(out_dir, ".__write_test__")
            with open(testfile, "w", encoding="utf-8") as t:
                t.write("ok")
            os.remove(testfile)
        except Exception:
            targets_path = os.path.join(tempfile.gettempdir(), targets_name)

        return targets_path

    return os.path.join(tempfile.gettempdir(), targets_name)


def check_column_exists(data, column):
    if column not in data.columns:
        print(f"Error: Column '{column}' not found in input data")
        sys.exit(1)


def check_column_numeric(data, column):
    if not pd.api.types.is_numeric_dtype(data[column]):
        print(f"Error: Column '{column}' must be numeric")
        sys.exit(1)


def haversine_distance(lat1, lon1, lat2, lon2):
    """Calculate great-circle distance between two points in meters."""
    R = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c


def process_gradiometer(data, mark_indices, tmi_lower_col, tmi_upper_col, altitude_col, sensor_separation):
    """Process marked points using gradiometer mode (two sensors)."""
    distances = []
    depths = []
    weights = []

    for idx in mark_indices:
        tmi_lower = data[tmi_lower_col].iloc[idx]
        tmi_upper = data[tmi_upper_col].iloc[idx]
        altitude_agl = data[altitude_col].iloc[idx]

        if pd.isna(tmi_lower) or pd.isna(tmi_upper) or pd.isna(altitude_agl):
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        gradient = (tmi_lower - tmi_upper) / sensor_separation

        if gradient == 0:
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        distance = -3.0 * tmi_lower / gradient
        if distance < 0:
            distance = abs(distance)

        depth = distance - altitude_agl
        if depth < 0:
            depth = 0

        weight = abs(gradient) * distance ** 4 / 84.944

        distances.append(distance)
        depths.append(depth)
        weights.append(weight)

    return distances, depths, weights


def process_single_sensor(data, mark_indices, tmi_col, altitude_col, lat_col, lon_col):
    """Process marked points using single sensor mode (synthetic gradient from flight track)."""
    distances = []
    depths = []
    weights = []

    for idx in mark_indices:
        tmi_i = data[tmi_col].iloc[idx]
        altitude_i = data[altitude_col].iloc[idx]

        if idx == 0 or idx == len(data) - 1:
            print(f"Warning: Marked point at index {idx} is at boundary, skipping")
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        lat_prev = data[lat_col].iloc[idx - 1]
        lon_prev = data[lon_col].iloc[idx - 1]
        lat_next = data[lat_col].iloc[idx + 1]
        lon_next = data[lon_col].iloc[idx + 1]
        tmi_prev = data[tmi_col].iloc[idx - 1]
        tmi_next = data[tmi_col].iloc[idx + 1]
        alt_prev = data[altitude_col].iloc[idx - 1]
        alt_next = data[altitude_col].iloc[idx + 1]

        values = [tmi_i, altitude_i, lat_prev, lon_prev, lat_next, lon_next,
                  tmi_prev, tmi_next, alt_prev, alt_next]
        if any(pd.isna(v) for v in values):
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        d_horiz = haversine_distance(lat_prev, lon_prev, lat_next, lon_next)
        d_alt = alt_next - alt_prev
        d_3d = math.sqrt(d_horiz ** 2 + d_alt ** 2)

        if d_3d == 0:
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        gradient = (tmi_next - tmi_prev) / (2 * d_3d)

        if gradient == 0:
            distances.append(np.nan)
            depths.append(np.nan)
            weights.append(np.nan)
            continue

        distance = -3.0 * tmi_i / gradient
        if distance < 0:
            distance = abs(distance)

        depth = distance - altitude_i
        if depth < 0:
            depth = 0

        weight = abs(gradient) * distance ** 4 / 84.944

        distances.append(distance)
        depths.append(depth)
        weights.append(weight)

    return distances, depths, weights


def main():
    parser = argparse.ArgumentParser(
        description="Estimates weight and depth from MagNIMBUS gradiometer or single-sensor data."
    )

    parser.add_argument("file_path", help='File path')
    parser.add_argument("--lower-sensor-column", default="TMI_lower",
                        help="Lower sensor column (default: TMI_lower)")
    parser.add_argument("--upper-sensor-column", default="TMI_upper",
                        help="Upper sensor column (default: TMI_upper)")
    parser.add_argument("--altitude-column", default="Altitude_AGL",
                        help="Altitude AGL column (default: Altitude_AGL)")
    parser.add_argument("--sensor-separation", type=float, default=1.5,
                        help="Sensor separation in meters (default: 1.5)")
    parser.add_argument("--save-to", dest="save_to", nargs="?", const=True, default=False, type=str2bool)
    parser.add_argument("--out-dir", dest="out_dir", type=str, default="")
    args = parser.parse_args()

    input_path = args.file_path
    tmi_lower_col = args.lower_sensor_column
    tmi_upper_col = args.upper_sensor_column
    altitude_col = args.altitude_column

    print(f"Reading file {input_path}")
    data = pd.read_csv(input_path)

    # Step 1: Validate required columns
    check_column_exists(data, tmi_lower_col)
    check_column_exists(data, altitude_col)

    if "Mark" not in data.columns:
        print("Error: Column 'Mark' not found in input data")
        sys.exit(1)

    # Step 2: Check for marked points
    marks = pd.to_numeric(data["Mark"], errors="coerce").fillna(0)
    mark_indices = marks[marks == 1].index.tolist()

    if len(mark_indices) == 0:
        print("No marked points found (Mark = 1). Nothing to do.")
        sys.exit(0)

    print(f"Found {len(mark_indices)} marked points")

    # Step 3: Determine mode
    gradiometer_mode = False
    if tmi_upper_col in data.columns:
        upper_data = pd.to_numeric(data[tmi_upper_col], errors="coerce")
        if upper_data.notna().any():
            gradiometer_mode = True

    if gradiometer_mode:
        print("Mode: Gradiometer (two sensors)")
        distances, depths, weights = process_gradiometer(
            data, mark_indices, tmi_lower_col, tmi_upper_col, altitude_col,
            args.sensor_separation
        )
    else:
        print("Mode: Single sensor (synthetic gradient)")
        check_column_exists(data, "Latitude")
        check_column_exists(data, "Longitude")
        distances, depths, weights = process_single_sensor(
            data, mark_indices, tmi_lower_col, altitude_col, "Latitude", "Longitude"
        )

    # Step 5: Save results - add columns with empty string for non-marked rows
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

    # Overwrite original CSV
    output_path = input_path
    print(f"Writing result to {output_path}")
    data.to_csv(output_path, index=False, sep=',')

    # Create targets file with only marked rows
    targets_path = build_targets_path(input_path, args.save_to, args.out_dir)
    targets_data = data.loc[mark_indices]
    print(f"Writing targets file to {targets_path}")
    targets_data.to_csv(targets_path, index=False, sep=',')

    print("Done")


if __name__ == "__main__":
    main()
