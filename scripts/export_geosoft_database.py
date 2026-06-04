import argparse
import importlib
import os
import re
import sys
import warnings
import numpy as np
import pandas as pd
from pyproj import Transformer
import geosoft.gxpy as gxpy
import geosoft.gxpy.gdb as gxdb

from script_utils import normalize_input_stem


_CHANNEL_NAME_RE = re.compile(r"[^A-Za-z0-9_]")

_UNIT_HINTS = (
    (re.compile(r"^heading$", re.I), "deg"),
    (re.compile(r"altitude", re.I), "m"),
    (re.compile(r"^tmi|^b[xyz]", re.I), "nT"),
)


def sanitize_channel_name(name):
    s = _CHANNEL_NAME_RE.sub("_", str(name)).strip("_")
    if not s:
        s = "channel"
    if not s[0].isalpha():
        s = "C_" + s
    return s


def utm_zone(latitude, longitude):
    zone = int((longitude + 180.0) / 6.0) + 1

    # Norway exception: band V (56-64°N), lon 3-6°E uses zone 32, not 31
    if 56.0 <= latitude < 64.0 and 3.0 <= longitude < 6.0:
        return 32

    # Svalbard exception: band X (72-84°N), even zones 32/34/36 are skipped
    if 72.0 <= latitude < 84.0:
        if 0.0 <= longitude < 9.0:
            return 31
        if 9.0 <= longitude < 21.0:
            return 33
        if 21.0 <= longitude < 33.0:
            return 35
        if 33.0 <= longitude < 42.0:
            return 37

    return zone


def utm_epsg(latitude, longitude):
    zone = utm_zone(latitude, longitude)
    return (32600 if latitude >= 0 else 32700) + zone


def project_to_utm(data, x_col, y_col, epsg):
    lon = data[x_col].to_numpy(dtype=np.float64)
    lat = data[y_col].to_numpy(dtype=np.float64)
    transformer = Transformer.from_crs(4326, epsg, always_xy=True)
    easting, northing = transformer.transform(lon, lat)
    data[x_col] = easting
    data[y_col] = northing


def build_output_gdb_path(input_path, output_dir):
    stem = os.path.splitext(os.path.basename(input_path))[0]
    stem = normalize_input_stem(stem)

    if not output_dir or not output_dir.strip():
        output_dir = os.path.dirname(os.path.abspath(input_path))
    else:
        output_dir = os.path.expanduser(os.path.expandvars(output_dir.strip()))

    os.makedirs(output_dir, exist_ok=True)
    return os.path.join(output_dir, stem + ".gdb")


def build_channel_names(columns, x_col, y_col):
    rename = {}
    if x_col:
        rename[x_col] = "X"
    if y_col:
        rename[y_col] = "Y"

    used = set()
    names = []
    for col in columns:
        name = rename.get(col, sanitize_channel_name(col))
        base = name
        i = 1
        while name.lower() in used:
            i += 1
            name = f"{base}_{i}"
        used.add(name.lower())
        names.append(name)
    return names


def unit_for(column):
    for pattern, unit in _UNIT_HINTS:
        if pattern.search(column):
            return unit
    return None


def main():
    parser = argparse.ArgumentParser(
        description="Export CSV measurements as a Geosoft Oasis Montaj database (.gdb)."
    )
    parser.add_argument("file_path", help="Input CSV file path")
    parser.add_argument("--output-dir", default="",
                        help="Output directory for the .gdb (default: same as input)")
    args = parser.parse_args()

    if not os.path.isfile(args.file_path):
        print(f"Error: input file not found: {args.file_path}")
        sys.exit(1)

    try:
        gxpy.gx.GXpy()
    except Exception as e:
        print("Error: Geosoft Oasis Montaj is not installed or not configured "
              "on this machine. Install Oasis Montaj (or Geosoft Desktop "
              f"Applications) and try again. Details: {e}")
        sys.exit(1)

    print(f"Reading {args.file_path}")
    data = pd.read_csv(args.file_path)
    print(f"Rows: {len(data)}, columns: {len(data.columns)}")

    if len(data) == 0:
        print("Error: input CSV has no rows")
        sys.exit(1)

    numeric_columns = []
    skipped = []
    for col in data.columns:
        if pd.api.types.is_numeric_dtype(data[col]):
            numeric_columns.append(col)
        else:
            skipped.append(col)
    if skipped:
        print(f"Skipping non-numeric columns: {skipped}")
    if not numeric_columns:
        print("Error: no numeric columns to export")
        sys.exit(1)

    x_col = "Longitude" if "Longitude" in numeric_columns else None
    y_col = "Latitude" if "Latitude" in numeric_columns else None
    if x_col and y_col:
        print(f"Using X = '{x_col}', Y = '{y_col}'")

    cs_label = None
    if x_col and y_col:
        # all-NaN slice raises RuntimeWarning; NaN is handled by isfinite below
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", RuntimeWarning)
            center_lat = float(np.nanmean(data[y_col].to_numpy(dtype=np.float64)))
            center_lon = float(np.nanmean(data[x_col].to_numpy(dtype=np.float64)))
        if not (np.isfinite(center_lat) and np.isfinite(center_lon)):
            print("Error: Latitude/Longitude columns contain no valid values")
            sys.exit(1)
        target_epsg = utm_epsg(center_lat, center_lon)
        zone = utm_zone(center_lat, center_lon)
        hemi = "N" if center_lat >= 0 else "S"
        print(f"Projecting X/Y to UTM zone {zone}{hemi} (EPSG:{target_epsg}) "
              f"from center ({center_lat:.5f}, {center_lon:.5f})")
        project_to_utm(data, x_col, y_col, target_epsg)
        # Geosoft requires a catalog name here; "EPSG:NNNNN" raises CSException
        cs_label = f"WGS 84 / UTM zone {zone}{hemi}"

    # Geosoft convention: X, Y first.
    head = [c for c in (x_col, y_col) if c]
    columns = head + [c for c in numeric_columns if c not in head]
    channel_names = build_channel_names(columns, x_col, y_col)
    print(f"Channels: {channel_names}")

    output_path = build_output_gdb_path(args.file_path, args.output_dir)
    base, _ = os.path.splitext(output_path)
    print(f"Creating database {base}.gdb")

    gdb = gxdb.Geosoft_gdb.new(base, overwrite=True)
    try:
        line_name = gxdb.create_line_name()
        values = data[columns].to_numpy(dtype=np.float64)
        print(f"Writing line '{line_name}' with {len(values)} rows")
        gdb.write_line(line_name, values, channel_names)

        if cs_label:
            gdb.coordinate_system = cs_label

        xy_originals = {c for c in (x_col, y_col) if c}
        for original, channel in zip(columns, channel_names):
            if cs_label and original in xy_originals:
                unit = "m"
            else:
                unit = unit_for(original)
            if unit:
                gxdb.Channel(gdb, channel).unit_of_measure = unit
    finally:
        gdb.close()

    print(f"Geosoft database saved to {output_path}")


if __name__ == "__main__":
    main()
