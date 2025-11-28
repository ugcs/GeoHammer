# -*- coding: utf-8 -*-

import os
import struct
import argparse
from datetime import datetime, timedelta

import numpy as np
import pandas as pd
from scipy.optimize import curve_fit
from scipy.stats import norm
import segyio
from segyio import TraceField as TF


# Decode vendor header data

def decode_vendor_header_240(raw240):
    altitude_m = struct.unpack("<f", raw240[40:44])[0]
    dir_raw   = struct.unpack("<i", raw240[48:52])[0]
    lon_deg   = struct.unpack("<d", raw240[182:190])[0]
    lat_deg   = struct.unpack("<d", raw240[190:198])[0]
    return altitude_m, dir_raw, lon_deg, lat_deg


# Detect marks using extended header pattern

def detect_marks_and_layout(filename):
    marks = []
    mark_vals = []

    def u16(b, endian="<"):
        return struct.unpack(endian + "H", b)[0]

    def u32(b, endian="<"):
        return struct.unpack(endian + "I", b)[0]

    valid_codes = {1: 4, 2: 4, 3: 2, 5: 4, 8: 1}

    with open(filename, "rb") as f:
        f.seek(0, 2)
        file_size = f.tell()

        f.seek(3200)
        binhdr = f.read(400)

        fmt_code = u16(binhdr[24:26], "<")
        endian = "<" if fmt_code in valid_codes else ">"

        bytes_per_sample = valid_codes.get(fmt_code, 2)

        f.seek(3600)
        trchdr = f.read(240)
        ns = u16(trchdr[114:116], endian)

        trace_size = 240 + ns * bytes_per_sample
        ntraces = (file_size - 3600) // trace_size

        pattern = b"\x55\x55"

        for i in range(int(ntraces)):
            f.seek(3600 + i * trace_size)
            header_ext = f.read(2000)
            if len(header_ext) < 4:
                break

            pos = header_ext.find(pattern)
            if pos >= 0 and pos + 4 <= len(header_ext):
                val = u32(header_ext[pos:pos+4], endian)
                if (val & 0xFFFF) == 0x5555:
                    mark = (val >> 16) & 0xFF
                    if mark == 255:
                        mark = 0
                    if mark != 0 and i > 0:
                        marks.append(i - 1)
                        mark_vals.append(mark)

    print("Found marks:", len(marks))

    return marks, mark_vals, dict(
        endian=endian,
        nsamples=ns,
        bytes_per_sample=bytes_per_sample,
        trace_size=trace_size,
        ntraces=int(ntraces),
    )


# Velocity helpers

VRES_MM_PER_S = 7.3921
LOOK_ANGLE_DEG = 45
VMASK_CM_PER_S = 15.0
VLOWER_CM_PER_S = -300.0
VUPPER_CM_PER_S = 300.0

def peakmodel1(v, mu1, s1, f1):
    return f1 * norm.pdf((v - mu1) / s1)

def build_velocity_axis(nsamples):
    angle = np.deg2rad(LOOK_ANGLE_DEG)
    cos_term = np.cos(angle)
    vres = VRES_MM_PER_S

    half = nsamples // 2
    neg = np.linspace(-half, -1, half) * vres
    pos = np.linspace(1, half, half) * vres
    vaxis_mm_s = np.concatenate((neg, pos))
    return (vaxis_mm_s / 10.0) / cos_term  # cm/s


def compute_velocities_for_mark_from_spectrum(sub_mean, vaxis_cm_s):
    valid = (vaxis_cm_s > VLOWER_CM_PER_S) & (vaxis_cm_s < VUPPER_CM_PER_S)
    v_fit = vaxis_cm_s[valid]
    p_fit = sub_mean[valid]

    blind = np.abs(v_fit) >= VMASK_CM_PER_S
    v_fit = v_fit[blind]
    p_fit = p_fit[blind]

    if len(v_fit) < 10:
        return np.nan, np.nan

    idx_max = np.argmax(p_fit)
    v_raw = float(v_fit[idx_max])

    p0 = [v_raw, 10.0, float(p_fit.max())]

    try:
        popt, _ = curve_fit(peakmodel1, v_fit, p_fit, p0=p0, maxfev=20000)
        v_fw = float(popt[0])
    except Exception:
        v_fw = np.nan

    return v_raw, v_fw


# MAIN

def main():
    parser = argparse.ArgumentParser(description="")
    parser.add_argument("file_path", help="SEG-Y file path")
    args = parser.parse_args()

    SEG_Y_FILE = args.file_path
    if not SEG_Y_FILE:
        print("No SGY selected. Exiting.")
        return

    # Auto-generate output CSV
    OUTPUT_CSV = os.path.splitext(SEG_Y_FILE)[0] + "_RSS.csv"

    print("\nUsing SGY:", SEG_Y_FILE)
    print("Saving CSV to:", OUTPUT_CSV, "\n")

    mark_indices, mark_values, layout = detect_marks_and_layout(SEG_Y_FILE)
    ns = layout["nsamples"]
    trace_size = layout["trace_size"]

    with segyio.open(SEG_Y_FILE, strict=False, endian="little") as f:
        ntraces = f.tracecount
        traces = np.stack([np.copy(f.trace[i]) for i in range(ntraces)], axis=1)

        if ns % 2 == 0:
            half = ns // 2
            traces = np.concatenate((traces[half:], traces[:half]), axis=0)

        vaxis_cm_s = build_velocity_axis(ns)
        rows = []
        base_pos = 3600

        for i, (mark, t_idx) in enumerate(zip(mark_values, mark_indices)):
            print("Processing mark", mark, "at trace", t_idx)

            if i < len(mark_indices) - 1:
                next_idx = mark_indices[i + 1]
                end_idx = t_idx + (next_idx - t_idx) // 2
            else:
                end_idx = ntraces - 1

            end_idx = max(end_idx, t_idx)
            end_idx = min(end_idx, ntraces - 1)

            sub = traces[:, t_idx:end_idx + 1]
            sub_mean = sub.mean(axis=1)

            v_raw, v_fw = compute_velocities_for_mark_from_spectrum(sub_mean, vaxis_cm_s)

            h = f.header[t_idx]
            year_raw = h.get(TF.YearDataRecorded)
            yday = h.get(TF.DayOfYear)
            hour = h.get(TF.HourOfDay, 0)
            minute = h.get(TF.MinuteOfHour, 0)
            second = h.get(TF.SecondOfMinute, 0)

            time_utc = np.nan
            try:
                if year_raw and yday:
                    year = 1900 + year_raw if year_raw < 200 else year_raw
                    dt = datetime(int(year), 1, 1) + timedelta(
                        days=int(yday) - 1,
                        hours=int(hour),
                        minutes=int(minute),
                        seconds=int(second),
                    )
                    time_utc = np.datetime64(dt)
            except Exception:
                pass

            with open(SEG_Y_FILE, "rb") as fh:
                fh.seek(base_pos + t_idx * trace_size)
                r240 = fh.read(240)

            altitude_m, dir_raw, lon_deg, lat_deg = decode_vendor_header_240(r240)

            rows.append([
                mark,
                t_idx,
                time_utc,
                lon_deg,
                lat_deg,
                altitude_m,
                dir_raw,
                v_raw,
                v_fw
            ])

    df = pd.DataFrame(rows, columns=[
        "Mark",
        "Trace",
        "Time stamp (UTC)",
        "Longitude (deg)",
        "Latitude (deg)",
        "Altitude (m)",
        "Direction (vendor)",
        "Velocity raw cm/s",
        "Velocity full waveform data cm/s",
    ])

    #Velocity in m/s
    df["Velocity raw m/s"] = df["Velocity raw cm/s"] / 100.0
    df["Velocity full waveform data m/s"] = df["Velocity full waveform data cm/s"] / 100.0

    if len(df) >= 2:
        lon0 = df["Longitude (deg)"].iloc[0]
        lat0 = df["Latitude (deg)"].iloc[0]
        lonN = df["Longitude (deg)"].iloc[-1]
        latN = df["Latitude (deg)"].iloc[-1]

        vx = lonN - lon0
        vy = latN - lat0
        norm2 = vx * vx + vy * vy

        if norm2 > 0:
            t = []
            for i in range(len(df)):
                dx = df["Longitude (deg)"].iloc[i] - lon0
                dy = df["Latitude (deg)"].iloc[i] - lat0
                ti = (dx * vx + dy * vy) / norm2
                t.append(ti)
            df["AxisCoord"] = t

            df["AxisSlope"] = df["AxisCoord"].diff()

            df["Direction"] = df["AxisSlope"].apply(lambda x: 1 if x >= 0 else -1)

            df["Direction"] = df["Direction"].rolling(
                window=5, center=True, min_periods=1
            ).median().astype(int)

            df.drop(columns=["AxisCoord", "AxisSlope"], inplace=True)
        else:
            df["Direction"] = df["Direction (vendor)"].apply(
                lambda d: -1 if d < 0 else 1
            )
    else:
        df["Direction"] = df["Direction (vendor)"].apply(
            lambda d: -1 if d < 0 else 1
        )

    df.drop(columns=["Direction (vendor)"], inplace=True)

    df.to_csv(OUTPUT_CSV, index=False)
    print("\nSaved CSV:", OUTPUT_CSV)

    # AUTO-OPEN CSV
    import platform, subprocess
    system = platform.system()
    try:
        if system == "Windows":
            os.startfile(OUTPUT_CSV)
        elif system == "Darwin":
            subprocess.call(["open", OUTPUT_CSV])
        else:
            subprocess.call(["xdg-open", OUTPUT_CSV])
    except Exception as e:
        print("Could not open CSV automatically:", e)


if __name__ == "__main__":
    main()
