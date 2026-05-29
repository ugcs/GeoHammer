# -*- coding: utf-8 -*-

import argparse
import os
import re
import sys

import numpy as np
import pandas as pd

import geosoft.gxpy as gxpy
import geosoft.gxpy.gdb as gxdb

from script_utils import normalize_input_stem


# Geosoft channel name: ASCII letters/digits/underscore, must start with a letter.
_CHANNEL_NAME_RE = re.compile(r"[^A-Za-z0-9_]")

_UNIT_HINTS = (
    (re.compile(r"^longitude$|^latitude$|^heading$", re.I), "deg"),
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

    print(f"Reading {args.file_path}")
    data = pd.read_csv(args.file_path)
    print(f"Rows: {len(data)}, columns: {len(data.columns)}")

    x_col = "Longitude" if "Longitude" in data.columns else None
    y_col = "Latitude" if "Latitude" in data.columns else None
    if x_col and y_col:
        print(f"Using X = '{x_col}', Y = '{y_col}'")

    columns = []
    skipped = []
    for col in data.columns:
        if pd.api.types.is_numeric_dtype(data[col]):
            columns.append(col)
        else:
            skipped.append(col)
    if skipped:
        print(f"Skipping non-numeric columns: {skipped}")
    if not columns:
        print("Error: no numeric columns to export")
        sys.exit(1)

    # Geosoft convention: X, Y first.
    head = [c for c in (x_col, y_col) if c]
    columns = head + [c for c in columns if c not in head]
    channel_names = build_channel_names(columns, x_col, y_col)
    print(f"Channels: {channel_names}")

    output_path = build_output_gdb_path(args.file_path, args.output_dir)
    base, _ = os.path.splitext(output_path)
    print(f"Creating database {base}.gdb")

    gxc = gxpy.gx.GXpy()
    try:
        gdb = gxdb.Geosoft_gdb.new(base, overwrite=True)
        try:
            line_name = gxdb.create_line_name()
            values = data[columns].to_numpy(dtype=np.float64)
            print(f"Writing line '{line_name}' with {len(values)} rows")
            gdb.write_line(line_name, values, channel_names)

            if x_col and y_col:
                gdb.coordinate_system = "WGS 84"
                gdb.xyz_channels = ("X", "Y", "")

            for original, channel in zip(columns, channel_names):
                unit = unit_for(original)
                if unit:
                    gxdb.Channel(gdb, channel).unit_of_measure = unit
        finally:
            gdb.close()
    finally:
        del gxc

    print(f"Geosoft database saved to {output_path}")


if __name__ == "__main__":
    main()
