#!/usr/bin/env python3
"""
Generate a MBES Patch Test flight route from a single surveyed SVLOG line.

L1 is the surveyed line, L3 and L5 are offset from it by half a swath to either side,
and L2, L4, L6 are those three reversed. The offset is derived from the depth of every
ping, so L3-L6 follow the bottom and come out as curves.

The input file holds the packets of one line only: GeoHammer writes that temporary
copy before the script starts.
"""
import argparse
import math
import os
import struct
import sys
import tempfile
from xml.sax.saxutils import escape

from script_utils import normalize_input_stem

# cross-track TX beam width of the Cerulean Surveyor
BEAM_WIDTH_DEG = 80.0

# svlog framing: b'BR', u16 payload length, u16 packet id, 2 reserved, payload, u16 checksum
HEADER_SIZE = 8
NMEA_WRAPPER_ID = 109
SURVEYOR_ATOF_POINT_DATA_ID = 3012

# spacing stays well below the swath width (1.7 * depth) so the offset curve of L3-L6
# still follows the bottom in shallow water; the tangent window keeps the direction
# base at ~20 m, otherwise the normal picks up GPS noise
WAYPOINT_SPACING_M = 2.0
TANGENT_HALF_WINDOW = 5     # resampled points on each side of the tangent

# the dense spacing above is what the offset needs, not what the route needs: waypoints
# are thinned out again where the line runs straight, keeping the shape within this much
SIMPLIFY_TOLERANCE_M = 0.5

MEDIAN_WINDOW = 5           # pings, odd
MIN_DEPTH_CHANGE = 2.0

EARTH_RADIUS_M = 6378137.0

FLIGHT_ORDER = ("L1", "L4", "L5", "L2", "L3", "L6")


def read_packets(path):
    with open(path, "rb") as f:
        data = f.read()

    packets = []
    offset = 0
    while offset + HEADER_SIZE <= len(data):
        if data[offset:offset + 2] != b"BR":
            raise ValueError(f"Sync lost at offset {offset}: expected b'BR'")

        payload_length, packet_id = struct.unpack_from("<HH", data, offset + 2)
        payload_start = offset + HEADER_SIZE
        checksum_start = payload_start + payload_length
        if checksum_start + 2 > len(data):
            break

        checksum = struct.unpack_from("<H", data, checksum_start)[0]
        if sum(data[offset:checksum_start]) & 0xFFFF == checksum:
            packets.append((packet_id, data[payload_start:checksum_start]))

        offset = checksum_start + 2

    return packets


def parse_nmea_location(payload):
    try:
        sentence = payload.decode("ascii", errors="ignore").strip()
    except UnicodeDecodeError:
        return None
    if not sentence.startswith("$"):
        return None

    fields = sentence.split(",")
    kind = fields[0][3:] if len(fields[0]) >= 6 else ""
    if kind == "GGA":
        lat, lat_hem, lon, lon_hem = fields[2:6] if len(fields) > 5 else ("", "", "", "")
    elif kind == "RMC":
        if len(fields) > 6 and fields[2] != "A":
            return None
        lat, lat_hem, lon, lon_hem = fields[3:7] if len(fields) > 6 else ("", "", "", "")
    else:
        return None

    latitude = _parse_degrees(lat, lat_hem, 2)
    longitude = _parse_degrees(lon, lon_hem, 3)
    if latitude is None or longitude is None:
        return None
    return latitude, longitude


def _parse_degrees(value, hemisphere, degree_digits):
    if not value or len(value) <= degree_digits:
        return None
    try:
        degrees = float(value[:degree_digits])
        minutes = float(value[degree_digits:])
    except ValueError:
        return None
    result = degrees + minutes / 60.0
    if hemisphere in ("S", "W"):
        result = -result
    return result


def parse_depth(payload):
    # 0:u32 pwr_up_msec, 4:u64 utc_msec, 12:float listening_sec, 16:float sos_mps,
    # 20:u32 ping_number, 24:u32 ping_hz, 28:float pulse_sec, 32:u32 flags,
    # 36:u16 num_points, 38:u16 reserved, 40: atof_t[num_points]
    # atof_t: 0:float angle (rad, positive to port), 4:float tof (s), 8:u32 reserved[2]
    if len(payload) < 40:
        return None

    speed_of_sound = struct.unpack_from("<f", payload, 16)[0]
    num_points = struct.unpack_from("<H", payload, 36)[0]
    if num_points == 0 or len(payload) < 40 + num_points * 16:
        return None

    min_angle = float("inf")
    depth = None
    for i in range(num_points):
        angle, time_of_flight = struct.unpack_from("<ff", payload, 40 + i * 16)
        if abs(angle) < min_angle:
            min_angle = abs(angle)
            distance = 0.5 * speed_of_sound * time_of_flight
            depth = distance * math.cos(angle)
    return depth


def read_pings(path):
    pings = []
    location = None
    for packet_id, payload in read_packets(path):
        if packet_id == NMEA_WRAPPER_ID:
            parsed = parse_nmea_location(payload)
            if parsed is not None:
                location = parsed
        elif packet_id == SURVEYOR_ATOF_POINT_DATA_ID:
            depth = parse_depth(payload)
            if depth is None or not depth > 0.0 or location is None:
                continue
            pings.append((location[0], location[1], depth))
    return pings


def smooth_depth(pings):
    half = MEDIAN_WINDOW // 2
    smoothed = []
    for i in range(len(pings)):
        window = [p[2] for p in pings[max(0, i - half):i + half + 1]]
        window.sort()
        smoothed.append((pings[i][0], pings[i][1], window[len(window) // 2]))
    return smoothed


def to_local(pings):
    # metric plane, x east, y north, origin at the first ping
    latitude0, longitude0 = pings[0][0], pings[0][1]
    scale = math.cos(math.radians(latitude0))
    track = []
    for latitude, longitude, depth in pings:
        x = math.radians(longitude - longitude0) * EARTH_RADIUS_M * scale
        y = math.radians(latitude - latitude0) * EARTH_RADIUS_M
        track.append((x, y, depth))
    return track, latitude0, longitude0


def to_geodetic(track, latitude0, longitude0):
    scale = math.cos(math.radians(latitude0))
    points = []
    for x, y, _depth in track:
        latitude = latitude0 + math.degrees(y / EARTH_RADIUS_M)
        longitude = longitude0 + math.degrees(x / (EARTH_RADIUS_M * scale))
        points.append((latitude, longitude))
    return points


def track_length(track):
    length = 0.0
    for i in range(1, len(track)):
        length += math.hypot(track[i][0] - track[i - 1][0], track[i][1] - track[i - 1][1])
    return length


def resample(track):
    resampled = [track[0]]
    remaining = WAYPOINT_SPACING_M
    for i in range(1, len(track)):
        x0, y0, d0 = track[i - 1]
        x1, y1, d1 = track[i]
        segment = math.hypot(x1 - x0, y1 - y0)
        if segment <= 0.0:
            continue
        position = 0.0
        while position + remaining <= segment:
            position += remaining
            t = position / segment
            resampled.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, d0 + (d1 - d0) * t))
            remaining = WAYPOINT_SPACING_M
        remaining -= segment - position
    if resampled[-1] != track[-1]:
        resampled.append(track[-1])
    return resampled


def simplify(track):
    # Douglas-Peucker: keep the points the shape depends on, drop the rest
    if len(track) < 3:
        return track

    keep = [False] * len(track)
    keep[0] = True
    keep[-1] = True
    pending = [(0, len(track) - 1)]
    while pending:
        first, last = pending.pop()
        farthest = -1
        farthest_distance = 0.0
        for i in range(first + 1, last):
            distance = _distance_to_segment(track[i], track[first], track[last])
            if distance > farthest_distance:
                farthest_distance = distance
                farthest = i
        if farthest > 0 and farthest_distance > SIMPLIFY_TOLERANCE_M:
            keep[farthest] = True
            pending.append((first, farthest))
            pending.append((farthest, last))

    return [point for point, kept in zip(track, keep) if kept]


def _distance_to_segment(point, start, end):
    dx, dy = end[0] - start[0], end[1] - start[1]
    length2 = dx * dx + dy * dy
    if length2 == 0.0:
        return math.hypot(point[0] - start[0], point[1] - start[1])
    t = ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / length2
    t = max(0.0, min(1.0, t))
    return math.hypot(point[0] - (start[0] + t * dx), point[1] - (start[1] + t * dy))


def offset(track, sign):
    half_angle = math.radians(BEAM_WIDTH_DEG / 2.0)
    shifted = []
    for i in range(len(track)):
        before = track[max(0, i - TANGENT_HALF_WINDOW)]
        after = track[min(len(track) - 1, i + TANGENT_HALF_WINDOW)]
        tx, ty = after[0] - before[0], after[1] - before[1]
        norm = math.hypot(tx, ty)
        if norm == 0.0:
            shifted.append(track[i])
            continue
        # normal to the right of the direction of travel
        nx, ny = ty / norm, -tx / norm
        distance = track[i][2] * math.tan(half_angle)
        shifted.append((track[i][0] + sign * nx * distance,
                        track[i][1] + sign * ny * distance,
                        track[i][2]))
    return shifted


def write_kml(lines, path, document_name):
    parts = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<kml xmlns="http://www.opengis.net/kml/2.2">',
             '<Document>',
             f'  <name>{escape(document_name)}</name>']
    for name in FLIGHT_ORDER:
        coordinates = " ".join(f"{longitude:.8f},{latitude:.8f},0"
                               for latitude, longitude in lines[name])
        parts.append('  <Placemark>')
        parts.append(f'    <name>{name}</name>')
        parts.append('    <LineString>')
        parts.append('      <altitudeMode>clampToGround</altitudeMode>')
        parts.append(f'      <coordinates>{coordinates}</coordinates>')
        parts.append('    </LineString>')
        parts.append('  </Placemark>')
    parts.append('</Document>')
    parts.append('</kml>')
    document = "\n".join(parts) + "\n"

    # write aside and rename, so an interrupted run leaves no half-written route
    directory = os.path.dirname(path) or "."
    handle, temporary = tempfile.mkstemp(dir=directory, suffix=".kml")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as f:
            f.write(document)
        os.replace(temporary, path)
    except BaseException:
        if os.path.exists(temporary):
            os.remove(temporary)
        raise


def main():
    parser = argparse.ArgumentParser(description="Generate a MBES Patch Test route as KML")
    parser.add_argument("input", help="SVLOG file with the packets of the selected line")
    parser.add_argument("--line", type=int, required=True, help="index of the selected line")
    parser.add_argument("--output-dir", required=True, help="folder for the generated KML")
    args = parser.parse_args()

    if not os.path.isdir(args.output_dir):
        sys.exit(f"Output folder does not exist: {args.output_dir}")

    try:
        pings = read_pings(args.input)
    except ValueError as e:
        sys.exit(f"Cannot read the selected line: {e}")
    if len(pings) < 2:
        sys.exit("Selected line has fewer than two pings with both position and depth")

    depths = [p[2] for p in pings]
    min_depth, max_depth = min(depths), max(depths)
    change = max_depth / min_depth if min_depth > 0.0 else float("inf")
    print(f"Line {args.line}: {len(pings)} pings, "
          f"depth {min_depth:.2f}-{max_depth:.2f} m, change {change:.2f}x")
    if change < MIN_DEPTH_CHANGE:
        print(f"WARNING: depth change is below {MIN_DEPTH_CHANGE:.0f}x, "
              f"the patch test may not resolve pitch and roll")

    track, latitude0, longitude0 = to_local(smooth_depth(pings))
    length = track_length(track)
    print(f"Line length {length:.1f} m, waypoint spacing {WAYPOINT_SPACING_M:.1f} m")
    if length < WAYPOINT_SPACING_M:
        sys.exit(f"Line is shorter ({length:.1f} m) than the waypoint spacing "
                 f"({WAYPOINT_SPACING_M:.1f} m)")

    # offsets need the dense track for a stable tangent; the route does not
    resampled = resample(track)
    l1 = simplify(resampled)
    l3 = simplify(offset(resampled, 1.0))
    l5 = simplify(offset(resampled, -1.0))
    lines = {
        "L1": to_geodetic(l1, latitude0, longitude0),
        "L2": to_geodetic(l1[::-1], latitude0, longitude0),
        "L3": to_geodetic(l3, latitude0, longitude0),
        "L4": to_geodetic(l3[::-1], latitude0, longitude0),
        "L5": to_geodetic(l5, latitude0, longitude0),
        "L6": to_geodetic(l5[::-1], latitude0, longitude0),
    }

    stem = normalize_input_stem(os.path.splitext(os.path.basename(args.input))[0])
    output_path = os.path.join(args.output_dir, f"{stem}_line{args.line}_patchtest.kml")
    write_kml(lines, output_path, f"{stem} patch test (line {args.line})")
    print(f"Patch test route saved to {output_path}")


if __name__ == "__main__":
    main()
