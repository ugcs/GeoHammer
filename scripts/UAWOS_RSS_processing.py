# -*- coding: utf-8 -*-

import os
import re
import struct
import argparse
from datetime import datetime, timedelta

import numpy as np
import pandas as pd
from scipy.optimize import curve_fit
from scipy.stats import norm
import segyio
from segyio import TraceField as TF


def normalize_input_stem(stem: str) -> str:
    s = stem.strip()

    # separators + long digits
    s2 = re.sub(r"([_\-\.\s])\d{10,}$", "", s)
    if s2 != s:
        return s2

    # parentheses with long digits
    s2 = re.sub(r"\s*\(\d{10,}\)$", "", s)
    if s2 != s:
        return s2.rstrip()

    # no separator, just a long digit tail
    s2 = re.sub(r"\d{12,}$", "", s)
    return s2



def build_output_csv_path(original_path: str, output_dir: str) -> str:
    stem = os.path.splitext(os.path.basename(original_path))[0]
    stem = normalize_input_stem(stem)
    out_name = stem + "_processed.csv"

    if not output_dir or not output_dir.strip():
        output_dir = os.path.dirname(os.path.abspath(original_path))

    output_dir = output_dir.strip()
    output_dir = os.path.expanduser(os.path.expandvars(output_dir))

    os.makedirs(output_dir, exist_ok=True)
    return os.path.join(output_dir, out_name)



# -----------------------------
# Decode vendor header data
# -----------------------------
def decode_vendor_header_240(raw240):
    altitude_m = struct.unpack("<f", raw240[40:44])[0]
    dir_raw   = struct.unpack("<i", raw240[48:52])[0]
    lon_deg   = struct.unpack("<d", raw240[182:190])[0]
    lat_deg   = struct.unpack("<d", raw240[190:198])[0]
    return altitude_m, dir_raw, lon_deg, lat_deg


# -----------------------------
# Detect marks using pattern
# -----------------------------
def detect_marks_and_layout(filename, scan_bytes=240, start_mark=1, pos_tolerance=2):
    marks = []
    mark_vals = []
    mark_pos = []

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


        fmt_le = u16(binhdr[24:26], "<")
        fmt_be = u16(binhdr[24:26], ">")

        if fmt_le in valid_codes:
            endian = "<"
            fmt_code = fmt_le
        elif fmt_be in valid_codes:
            endian = ">"
            fmt_code = fmt_be
        else:
            endian = "<"
            fmt_code = fmt_le

        bytes_per_sample = valid_codes.get(fmt_code, 2)
        # ------------------------------------------------------

        f.seek(3600)
        trchdr = f.read(240)
        ns = u16(trchdr[114:116], endian)

        trace_size = 240 + ns * bytes_per_sample
        ntraces = (file_size - 3600) // trace_size

        pattern = b"\x55\x55"

        for i in range(int(ntraces)):
            f.seek(3600 + i * trace_size)
            buf = f.read(scan_bytes)
            if len(buf) < 4:
                break

            pos = buf.find(pattern)
            if pos >= 0 and pos + 4 <= len(buf):
                val = u32(buf[pos:pos + 4], endian)
                if (val & 0xFFFF) == 0x5555:
                    mark = (val >> 16) & 0xFF
                    if mark == 255:
                        mark = 0

                    if mark != 0 and i > 0:
                        marks.append(i - 1)
                        mark_vals.append(int(mark))
                        mark_pos.append(int(pos))

    # False-positive filtering by byte position (keep only the dominant pos)
    if mark_pos:
        pos_mode = max(set(mark_pos), key=mark_pos.count)
        keep_idx = [k for k, p in enumerate(mark_pos) if abs(p - pos_mode) <= pos_tolerance]
        marks = [marks[k] for k in keep_idx]
        mark_vals = [mark_vals[k] for k in keep_idx]
        mark_pos = [mark_pos[k] for k in keep_idx]

    # Start skipper
    if mark_vals and start_mark is not None:
        try:
            first_ok = mark_vals.index(start_mark)
            marks = marks[first_ok:]
            mark_vals = mark_vals[first_ok:]
            mark_pos = mark_pos[first_ok:]
        except ValueError:
            marks, mark_vals, mark_pos = [], [], []

    print(f"Found marks after filtering: {len(marks)}")
    if mark_pos:
        print(f"Using dominant mark byte position: {max(set(mark_pos), key=mark_pos.count)}")

    return marks, mark_vals, dict(
        endian=endian,
        nsamples=ns,
        bytes_per_sample=bytes_per_sample,
        trace_size=trace_size,
        ntraces=int(ntraces),
        fmt_code=fmt_code,
    )


# -----------------------------
# Manual SEG-Y reader
# -----------------------------
def _ibm_to_ieee_f32(u32_arr: np.ndarray) -> np.ndarray:
    # Minimal IBM float -> IEEE float conversion (vectorized)
    # u32_arr is uint32 containing IBM floats.
    u = u32_arr.astype(np.uint32)
    sign = (u >> 31) & 0x1
    exp = (u >> 24) & 0x7F
    frac = u & 0x00FFFFFF

    out = np.zeros(u.shape, dtype=np.float32)
    nz = (exp != 0) & (frac != 0)
    if not np.any(nz):
        return out

    # fraction is base-16, 24-bit, interpreted as 0.frac in hex
    f = frac[nz].astype(np.float64) / float(0x01000000)  # 2^24
    e = exp[nz].astype(np.int32) - 64
    val = f * (16.0 ** e)

    out[nz] = val.astype(np.float32)
    out[sign.astype(bool)] *= -1.0
    return out


def read_traces_fallback(filename: str, layout: dict):
    endian = layout["endian"]  # "<" or ">"
    ns_ref = int(layout["nsamples"])
    bps = int(layout["bytes_per_sample"])
    fmt_code = int(layout.get("fmt_code", 5))

    def u16(b):
        return struct.unpack(endian + "H", b)[0]

    def s16(b):
        return struct.unpack(endian + "h", b)[0]

    traces_list = []
    headers_list = []

    with open(filename, "rb") as f:
        f.seek(3600)

        while True:
            h = f.read(240)
            if len(h) < 240:
                break

            ns_i = u16(h[114:116])
            if ns_i <= 0 or ns_i > 10000000:
                break

            data_bytes = f.read(ns_i * bps)
            if len(data_bytes) < ns_i * bps:
                break

            # decode samples
            if fmt_code == 5 and bps == 4:  # IEEE float
                dt = np.dtype(endian + "f4")
                x = np.frombuffer(data_bytes, dtype=dt).astype(np.float32, copy=False)
            elif fmt_code == 2 and bps == 4:  # int32
                dt = np.dtype(endian + "i4")
                x = np.frombuffer(data_bytes, dtype=dt).astype(np.float32)
            elif fmt_code == 3 and bps == 2:  # int16
                dt = np.dtype(endian + "i2")
                x = np.frombuffer(data_bytes, dtype=dt).astype(np.float32)
            elif fmt_code == 8 and bps == 1:  # int8
                x = np.frombuffer(data_bytes, dtype=np.int8).astype(np.float32)
            elif fmt_code == 1 and bps == 4:  # IBM float
                dt = np.dtype(endian + "u4")
                u = np.frombuffer(data_bytes, dtype=dt)
                x = _ibm_to_ieee_f32(u)
            else:
                # unknown
                dt = np.dtype(endian + "i2") if bps >= 2 else np.int8
                x = np.frombuffer(data_bytes, dtype=dt).astype(np.float32)

            # pad/truncate to ns_ref for consistent downstream processing
            if ns_i < ns_ref:
                x = np.pad(x, (0, ns_ref - ns_i), mode="constant")
            elif ns_i > ns_ref:
                x = x[:ns_ref]

            traces_list.append(x)
            headers_list.append(h)

    if not traces_list:
        raise RuntimeError("Could not read any traces from file.")

    traces = np.stack(traces_list, axis=1)  # (ns, ntraces)

    def get_time_fields(t_idx: int):
        h = headers_list[t_idx]
        year_raw = s16(h[156:158])
        yday = s16(h[158:160])
        hour = s16(h[160:162])
        minute = s16(h[162:164])
        second = s16(h[164:166])
        return year_raw, yday, hour, minute, second

    return traces, len(traces_list), get_time_fields


# -----------------------------
# Velocity helpers
# -----------------------------
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

# -----------------------------
# MAIN
# -----------------------------
def main():
    parser = argparse.ArgumentParser(description="")
    parser.add_argument("file_path", help="SEG-Y file path")
    parser.add_argument("--original-path", default="", help="Original file path (fallback, prefer GEOHAMMER_ORIGINAL_PATH env var)")
    parser.add_argument("--output-dir", dest="output_dir", type=str, default="")

    args = parser.parse_args()

    SEG_Y_FILE = args.file_path
    if not SEG_Y_FILE:
        print("No SGY selected. Exiting.")
        return

    original_path = os.environ.get('GEOHAMMER_ORIGINAL_PATH', '') or args.original_path or SEG_Y_FILE

    OUTPUT_CSV = build_output_csv_path(original_path, args.output_dir)

    print("\nUsing SGY:", SEG_Y_FILE)
    print("Saving CSV to:", OUTPUT_CSV, "\n")

    mark_indices, mark_values, layout = detect_marks_and_layout(SEG_Y_FILE)

    if not mark_indices or not mark_values:
        raise RuntimeError(
            "No marks found in this file. Please add marks, reload the file, and try again."
        )

    ns = layout["nsamples"]
    trace_size = layout["trace_size"]


    traces = None
    ntraces = None
    header_getter = None  # function(t_idx)->(year_raw,yday,hour,minute,second)
    f_segy = None

    endian_str = "little" if layout["endian"] == "<" else "big"
    try:
        f_segy = segyio.open(SEG_Y_FILE, strict=False, endian=endian_str)
    except RuntimeError:
        try:
            alt = "big" if endian_str == "little" else "little"
            f_segy = segyio.open(SEG_Y_FILE, strict=False, endian=alt)
        except RuntimeError:
            f_segy = None

    if f_segy is not None:
        with f_segy as f:
            ntraces = f.tracecount
            traces = np.stack([np.copy(f.trace[i]) for i in range(ntraces)], axis=1)

            def header_getter(t_idx: int):
                h = f.header[t_idx]
                year_raw = h.get(TF.YearDataRecorded)
                yday = h.get(TF.DayOfYear)
                hour = h.get(TF.HourOfDay, 0)
                minute = h.get(TF.MinuteOfHour, 0)
                second = h.get(TF.SecondOfMinute, 0)
                return year_raw, yday, hour, minute, second

            # rotate if even
            if ns % 2 == 0:
                half = ns // 2
                traces = np.concatenate((traces[half:], traces[:half]), axis=0)

            vaxis_cm_s = build_velocity_axis(ns)
            rows = []
            base_pos = 3600

            for i, (mark, t_idx) in enumerate(zip(mark_values, mark_indices)):
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

                year_raw, yday, hour, minute, second = header_getter(t_idx)

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
    else:
        # Fallback: tolerate padding / trailing bytes / non-uniform traces
        traces, ntraces, header_getter = read_traces_fallback(SEG_Y_FILE, layout)

        # rotate if even
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

            year_raw, yday, hour, minute, second = header_getter(t_idx)

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

    # Velocity in m/s
    df["Velocity raw m/s"] = df["Velocity raw cm/s"] / 100.0
    df["Velocity full waveform data m/s"] = df["Velocity full waveform data cm/s"] / 100.0

    # Direction calculation
    MAX_LINE_ANGLE_DEG = 20.0
    COS_THR = np.cos(np.deg2rad(MAX_LINE_ANGLE_DEG))
    EPS_LEN = 1e-15
    MIN_CONSEC_FOR_FLIP = 1

    n = len(df)
    if n >= 2:
        lon = df["Longitude (deg)"].astype(float).to_numpy()
        lat = df["Latitude (deg)"].astype(float).to_numpy()

        def vec(i0, i1):
            latm = 0.5 * (lat[i0] + lat[i1])
            dx = (lon[i1] - lon[i0]) * np.cos(np.deg2rad(latm))
            dy = (lat[i1] - lat[i0])
            return np.array([dx, dy], dtype=float)

        vref = vec(0, 1)
        nref = np.linalg.norm(vref)

        if not np.isfinite(nref) or nref < EPS_LEN:
            df["Direction"] = df["Direction (vendor)"].apply(lambda d: -1 if d < 0 else 1)
        else:
            refhat = vref / nref
            direction = np.ones(n, dtype=int)
            direction[0] = 1

            current = 1
            pending = 0
            pending_count = 0

            for i in range(0, n - 1):
                v = vec(i, i + 1)
                nv = np.linalg.norm(v)

                if not np.isfinite(nv) or nv < EPS_LEN:
                    direction[i] = current
                    continue

                vhat = v / nv
                c = float(np.dot(vhat, refhat))

                if abs(c) < COS_THR:
                    direction[i] = current
                    pending = 0
                    pending_count = 0
                    continue

                seg_sign = 1 if c >= 0 else -1

                if seg_sign == current:
                    pending = 0
                    pending_count = 0
                else:
                    if pending == seg_sign:
                        pending_count += 1
                    else:
                        pending = seg_sign
                        pending_count = 1

                    if pending_count >= MIN_CONSEC_FOR_FLIP:
                        current = seg_sign
                        pending = 0
                        pending_count = 0

                direction[i] = current

            direction[-1] = direction[-2]
            df["Direction"] = direction
    else:
        df["Direction"] = df["Direction (vendor)"].apply(lambda d: -1 if d < 0 else 1)

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
