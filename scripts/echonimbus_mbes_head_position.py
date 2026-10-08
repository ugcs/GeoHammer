#!/usr/bin/env python3
"""
Recalculate the MBES head position of an EchoNIMBUS (Cerulean Surveyor 240-16)
SVLOG file, in place, from better drone positions: a PPK .pos or a SkyHub position.csv.

The sonar head hangs on a two-link arm behind and below the GNSS antenna. The
driver logs into $GPGGA the head position: the antenna position plus an arm offset
from the drone heading and the sonar pitch and roll.
"""
import argparse
import bisect
import csv
import math
import struct
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

# svlog framing: b'BR', u16 payload length, u16 packet id, 2 reserved, payload, u16 checksum
HEADER_SIZE = 8
CHECKSUM_SIZE = 2
NMEA_WRAPPER_ID = 109
SURVEYOR_ATTITUDE_REPORT_ID = 504

# arm geometry, named as in the driver configuration (ROS_SONAR_CERULEANSURVEYOR_X).
# The specification defaults: they reproduce the head positions logged by the driver
# to a millimetre
FIRST_LINK_M = 0.79
SECOND_LINK_M = 0.77
MOUNTING_OFFSET_M = 0.14
SENSOR_MOUNTING_ANGLE_DEG = 45.0
LINKS_ANGLES_RATIO = 0.5

# WGS84
WGS84_A = 6378137.0
WGS84_E2 = 6.69437999014e-3

# GPS - UTC offset, seconds, by the UTC date it took effect
LEAP_SECONDS = (
    (datetime(2006, 1, 1, tzinfo=timezone.utc), 14),
    (datetime(2009, 1, 1, tzinfo=timezone.utc), 15),
    (datetime(2012, 7, 1, tzinfo=timezone.utc), 16),
    (datetime(2015, 7, 1, tzinfo=timezone.utc), 17),
    (datetime(2017, 1, 1, tzinfo=timezone.utc), 18),
)
GPS_EPOCH = datetime(1980, 1, 6, tzinfo=timezone.utc)

MICRO_MINUTES_PER_DEGREE = 60_000_000
HALF_DAY_S = 12 * 3600

# RTKLIB solution quality Q -> GGA fix quality. GGA has no code for PPP (6): it is
# mapped to a plain GPS fix so that it never replaces an RTK or DGPS position
POS_QUALITY_TO_GGA = {1: 4, 2: 5, 3: 2, 4: 2, 5: 1, 6: 1}
# GGA fix quality from best to worst. A solution with a code not listed here
# (0 = no fix, or an unknown .pos Q) never replaces a logged position
GGA_QUALITY_RANKS = {"4": 4, "5": 3, "2": 2, "1": 1}

# a position file is not interpolated across a gap longer than this: an outage
# of the PPK solution must not replace the logged positions with guessed ones.
# Allows 1 Hz solutions with some jitter
MAX_POSITION_GAP_S = 2.0


def arm_offset(pitch, roll, heading):
    # Data post processing section of the Surveyor 240-16 driver specification.
    # The specification has L2 * sin(P + alpha) in Lh; the driver uses cos,
    # which matches the logged positions to a millimetre. It also calls the second
    # axis west while its formula gives east. Returns north, east and up offsets
    # of the head from the antenna, metres
    link_tilt = math.radians(pitch + SENSOR_MOUNTING_ANGLE_DEG)
    first_tilt = math.radians(90.0 * (1.0 - LINKS_ANGLES_RATIO)) + LINKS_ANGLES_RATIO * link_tilt
    horizontal = FIRST_LINK_M * math.cos(first_tilt) + SECOND_LINK_M * math.cos(link_tilt)
    vertical = FIRST_LINK_M * math.sin(first_tilt) + SECOND_LINK_M * math.sin(link_tilt)

    h = math.radians(heading)
    r = math.radians(roll)
    north = -horizontal * math.cos(h) - vertical * math.sin(r) * math.sin(h)
    east = -horizontal * math.sin(h) + vertical * math.sin(r) * math.cos(h)
    up = -vertical * math.cos(r) - MOUNTING_OFFSET_M
    return north, east, up


class Fix:

    def __init__(self, time, latitude, longitude, altitude, quality=None):
        self.time = time
        self.latitude = latitude
        self.longitude = longitude
        self.altitude = altitude
        self.quality = quality


# svlog

def read_packets(data):
    packets = []
    offset = 0
    while offset + HEADER_SIZE <= len(data):
        if data[offset:offset + 2] != b"BR":
            raise ValueError(f"Sync lost at offset {offset}: expected b'BR'")
        payload_length, packet_id = struct.unpack_from("<HH", data, offset + 2)
        end = offset + HEADER_SIZE + payload_length + CHECKSUM_SIZE
        if end > len(data):
            print(f"Truncated packet at offset {offset} is kept as is")
            packets.append((None, bytes(data[offset:])))
            break
        packets.append((packet_id, bytes(data[offset:end])))
        offset = end
    return packets


def packet_payload(packet):
    return packet[HEADER_SIZE:-CHECKSUM_SIZE]


def build_packet(packet, payload):
    header = packet[:2] + struct.pack("<HH", len(payload), struct.unpack_from("<H", packet, 4)[0]) + packet[6:8]
    body = header + payload
    return body + struct.pack("<H", sum(body) & 0xFFFF)


# NMEA

def nmea_fields(payload):
    text = payload.decode("ascii", errors="ignore")
    start = text.find("$")
    if start < 0:
        return None, ""
    end = text.find("*", start)
    tail = text[end + 3:] if end >= 0 else ""
    body = text[start + 1:end] if end >= 0 else text[start + 1:].rstrip()
    return body.split(","), tail


def nmea_checksum(body):
    checksum = 0
    for c in body:
        checksum ^= ord(c)
    return f"{checksum:02X}"


def parse_nmea_angle(value, hemisphere, degree_digits):
    degrees = int(value[:degree_digits]) + float(value[degree_digits:]) / 60.0
    return -degrees if hemisphere in ("S", "W") else degrees


def format_nmea_angle(degrees, degree_digits):
    micro_minutes = round(abs(degrees) * MICRO_MINUTES_PER_DEGREE)
    whole, fraction = divmod(micro_minutes, MICRO_MINUTES_PER_DEGREE)
    return f"{whole:0{degree_digits}d}{fraction // 1_000_000:02d}.{fraction % 1_000_000:06d}"


def parse_time_of_day(value):
    return int(value[0:2]) * 3600 + int(value[2:4]) * 60 + float(value[4:])


class GgaRecord:
    """One $GPGGA of the stream with the heading and the sonar attitude at its moment."""

    def __init__(self, packet_index, fields, tail):
        self.packet_index = packet_index
        self.fields = fields
        self.tail = tail
        self.time_of_day = parse_time_of_day(fields[1])
        self.time = None
        self.heading = None
        self.attitude = None

    @property
    def quality(self):
        return self.fields[6]

    def format_gga(self, latitude, longitude, altitude, quality):
        fields = list(self.fields)
        fields[2] = format_nmea_angle(latitude, 2)
        fields[3] = "N" if latitude >= 0 else "S"
        fields[4] = format_nmea_angle(longitude, 3)
        fields[5] = "E" if longitude >= 0 else "W"
        fields[6] = quality
        # ellipsoidal height in the MSL altitude field with zero geoid separation,
        # as the driver writes it
        fields[9] = f"{altitude:.2f}"
        body = ",".join(fields)
        return f"${body}*{nmea_checksum(body)}{self.tail}".encode("ascii")


def parse_attitude(payload):
    # up_vec: vec3 at offset 0
    if len(payload) < 12:
        return None
    up_x, up_y, up_z = struct.unpack_from("<fff", payload, 0)
    pitch = math.degrees(math.asin(max(-1.0, min(1.0, up_x))))
    roll = math.degrees(math.atan2(up_y, up_z))
    return pitch, roll


def read_gga_records(packets):
    records = []
    last_attitude = None
    # (number of GGA records before it, date and time) of every $GPZDA
    dates = []
    for index, (packet_id, packet) in enumerate(packets):
        if packet_id == SURVEYOR_ATTITUDE_REPORT_ID:
            last_attitude = parse_attitude(packet_payload(packet)) or last_attitude
            continue
        if packet_id != NMEA_WRAPPER_ID:
            continue
        fields, tail = nmea_fields(packet_payload(packet))
        if not fields or len(fields[0]) < 5:
            continue
        sentence = fields[0][-3:]
        try:
            if sentence == "GGA":
                if len(fields) < 10 or not fields[2] or not fields[4] or not fields[9]:
                    continue
                record = GgaRecord(index, fields, tail)
                # the driver uses the last attitude report before the $GPGGA,
                # not an interpolated one
                record.attitude = last_attitude
                records.append(record)
            elif sentence == "HDT":
                # the driver writes $GPHDT after the $GPGGA of the same moment, before the next $GPGGA
                if records and records[-1].heading is None:
                    records[-1].heading = float(fields[1])
            elif sentence == "ZDA":
                time_of_day = parse_time_of_day(fields[1])
                day = datetime(int(fields[4]), int(fields[3]), int(fields[2]), tzinfo=timezone.utc)
                dates.append((len(records), day + timedelta(seconds=time_of_day)))
        except (ValueError, IndexError):
            continue

    if not records:
        raise SystemExit("No $GPGGA positions in the file")
    if not dates:
        raise SystemExit("No $GPZDA dates in the file")
    attitudes = fill_missing([record.attitude for record in records])
    if attitudes is None:
        raise SystemExit("No sonar attitude reports (packet 504) in the file")
    headings = fill_missing([record.heading for record in records])
    if headings is None:
        raise SystemExit("No $GPHDT headings in the file")
    for record, attitude, heading in zip(records, attitudes, headings):
        record.attitude = attitude
        record.heading = heading
    assign_times(records, dates)
    return records


def fill_missing(values):
    # a missing value takes the previous one; the values before the first present
    # one (a cropped file may start with them) take that first one. None if all missing
    previous = next((value for value in values if value is not None), None)
    if previous is None:
        return None
    filled = []
    for value in values:
        if value is not None:
            previous = value
        filled.append(previous)
    return filled


def assign_times(records, dates):
    # a GGA carries the time of day only: take the date of the $GPZDA that follows
    # it in the stream and step a day when the two straddle midnight
    zda_record_indices = [index for index, _ in dates]
    for i, record in enumerate(records):
        k = bisect.bisect_right(zda_record_indices, i)
        _, reference = dates[min(k, len(dates) - 1)]
        midnight = reference.replace(hour=0, minute=0, second=0, microsecond=0)
        time = midnight + timedelta(seconds=record.time_of_day)
        delta = (time - reference).total_seconds()
        if delta > HALF_DAY_S:
            time -= timedelta(days=1)
        elif delta < -HALF_DAY_S:
            time += timedelta(days=1)
        record.time = time.timestamp()


# position files

def gps_to_utc(time):
    if time - timedelta(seconds=LEAP_SECONDS[0][1]) < LEAP_SECONDS[0][0]:
        raise SystemExit(f"GPS time {time:%Y-%m-%d} is before {LEAP_SECONDS[0][0]:%Y-%m-%d}: "
                         "the GPS - UTC offset of that date is not known to the script")
    leap = 0
    for since, seconds in LEAP_SECONDS:
        if time - timedelta(seconds=seconds) >= since:
            leap = seconds
    return time - timedelta(seconds=leap)


def read_pos(path):
    # heights are taken as ellipsoidal heights of the antenna itself: no antenna
    # height and no geoid applied in the PPK software
    time_system = None
    fixes = []
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line.startswith("%"):
                header = line[1:].split()
                if header and header[0] in ("GPST", "UTC"):
                    time_system = header[0]
                    if "latitude(deg)" not in header or "longitude(deg)" not in header:
                        raise SystemExit("Only .pos files with latitude/longitude in degrees are supported")
                continue
            if time_system is None:
                raise SystemExit("Not a RTKLIB/Emlid .pos file: no '%  GPST' or '%  UTC' column header")
            tokens = line.split()
            try:
                if "/" in tokens[0]:
                    time = datetime.strptime(tokens[0] + " " + tokens[1], "%Y/%m/%d %H:%M:%S.%f")
                    time = time.replace(tzinfo=timezone.utc)
                else:
                    # GPS week and time of week
                    time = GPS_EPOCH + timedelta(weeks=int(tokens[0]), seconds=float(tokens[1]))
                if time_system == "GPST":
                    time = gps_to_utc(time)
                fixes.append(Fix(time.timestamp(), float(tokens[2]), float(tokens[3]),
                                 float(tokens[4]), int(tokens[5])))
            except (ValueError, IndexError):
                continue
    return fixes


def read_position_csv(path):
    fixes = []
    with open(path, "r", encoding="utf-8-sig", errors="replace", newline="") as f:
        reader = csv.DictReader(f)
        for column in ("Elapsed", "Latitude", "Longitude", "Ellipsoidal Height"):
            if column not in (reader.fieldnames or []):
                raise SystemExit(f"Not a SkyHub position.csv: no '{column}' column")
        for row in reader:
            try:
                fixes.append(Fix(int(row["Elapsed"]) / 1000.0, float(row["Latitude"]),
                                 float(row["Longitude"]), float(row["Ellipsoidal Height"])))
            except (ValueError, TypeError):
                continue
    return fixes


def read_position_file(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        head = f.read(4096)
    first_line = head.lstrip("﻿").split("\n", 1)[0]
    if head.lstrip().startswith("%"):
        fixes = read_pos(path)
    elif first_line.startswith("Elapsed,"):
        fixes = read_position_csv(path)
    else:
        raise SystemExit(f"Unknown position file format: {path.name}. "
                         "Expected a RTKLIB/Emlid .pos or a SkyHub position.csv")
    fixes.sort(key=lambda fix: fix.time)
    if len(fixes) < 2:
        raise SystemExit(f"No positions in the position file {path.name}")
    return fixes


# interpolation, as in GeoHammer Geotagger: a cubic Hermite spline over the four
# nearest fixes for latitude and longitude, linear for altitude

def tangent(xp, yp, xn, yn):
    dx = xn - xp
    return (yn - yp) / dx if abs(dx) > 1e-12 else 0.0


def hermite(x, xp, yp, xn, yn, tp, tn):
    h = xn - xp
    t = (x - xp) / h if abs(h) > 1e-12 else 0.0
    t2 = t * t
    t3 = t2 * t
    return ((2 * t3 - 3 * t2 + 1) * yp + (t3 - 2 * t2 + t) * h * tp
            + (-2 * t3 + 3 * t2) * yn + (t3 - t2) * h * tn)


def spline(x, p2, p1, n1, n2, coordinate):
    tp = tangent(p2.time, coordinate(p2), n1.time, coordinate(n1))
    tn = tangent(p1.time, coordinate(p1), n2.time, coordinate(n2))
    return hermite(x, p1.time, coordinate(p1), n1.time, coordinate(n1), tp, tn)


def interpolate(fixes, times, time):
    if time < fixes[0].time or time > fixes[-1].time:
        return None
    i = bisect.bisect_left(times, time)
    last = len(fixes) - 1
    p2 = fixes[max(i - 2, 0)]
    p1 = fixes[max(i - 1, 0)]
    n1 = fixes[min(i, last)]
    n2 = fixes[min(i + 1, last)]
    span = n1.time - p1.time
    if span > MAX_POSITION_GAP_S:
        return None

    latitude = spline(time, p2, p1, n1, n2, lambda fix: fix.latitude)
    longitude = spline(time, p2, p1, n1, n2, lambda fix: fix.longitude)
    altitude = p1.altitude if abs(span) < 1e-12 \
        else p1.altitude + (n1.altitude - p1.altitude) * (time - p1.time) / span
    # the larger RTKLIB Q of the bracketing fixes: the worse one for Q 1-5
    quality = None
    if p1.quality is not None and n1.quality is not None:
        quality = max(p1.quality, n1.quality)
    return Fix(time, latitude, longitude, altitude, quality)


# geodesy

def offset_position(latitude, longitude, altitude, north, east, up):
    phi = math.radians(latitude)
    w = 1.0 - WGS84_E2 * math.sin(phi) ** 2
    meridian = WGS84_A * (1.0 - WGS84_E2) / w ** 1.5
    normal = WGS84_A / math.sqrt(w)
    return (latitude + math.degrees(north / meridian),
            longitude + math.degrees(east / (normal * math.cos(phi))),
            altitude + up)


# recalculation

def is_worse_quality(quality, than):
    if quality not in GGA_QUALITY_RANKS:
        return True
    return GGA_QUALITY_RANKS[quality] < GGA_QUALITY_RANKS.get(than, 0)


def recalculate(path, position_path):
    data = path.read_bytes()
    packets = read_packets(data)
    records = read_gga_records(packets)

    fixes = read_position_file(position_path)
    times = [fix.time for fix in fixes]
    print(f"Position file: {len(fixes)} positions")
    if fixes[-1].time < records[0].time or fixes[0].time > records[-1].time:
        raise SystemExit("The position file does not overlap the file in time. "
                         "Check that the position file belongs to this flight")

    updated = 0
    without_position = 0
    worse_quality = 0
    for record in records:
        antenna = interpolate(fixes, times, record.time)
        if antenna is None:
            without_position += 1
            continue
        # position.csv carries no quality: it is the logged solution itself
        if antenna.quality is None:
            quality = record.quality
        else:
            quality = str(POS_QUALITY_TO_GGA.get(antenna.quality, 0))
            if is_worse_quality(quality, record.quality):
                worse_quality += 1
                continue

        pitch, roll = record.attitude
        north, east, up = arm_offset(pitch, roll, record.heading)
        latitude, longitude, altitude = offset_position(antenna.latitude, antenna.longitude, antenna.altitude,
                                                        north, east, up)
        payload = record.format_gga(latitude, longitude, altitude, quality)

        packet_id, packet = packets[record.packet_index]
        packets[record.packet_index] = (packet_id, build_packet(packet, payload))
        updated += 1

    path.write_bytes(b"".join(packet for _, packet in packets))
    print(f"Recalculated {updated} of {len(records)} positions")
    if without_position:
        print(f"Kept {without_position} positions unchanged: no data in the position file "
              f"(outside its time range or in a gap over {MAX_POSITION_GAP_S:g} s)")
    if worse_quality:
        print(f"Kept {worse_quality} positions unchanged: the position file solution is worse "
              "than the logged one (e.g. PPK float over RTK fixed)")


def main():
    parser = argparse.ArgumentParser(description="Recalculate the EchoNIMBUS MBES head position in an SVLOG file.")
    parser.add_argument("file", help="SVLOG file to modify in place")
    parser.add_argument("--position-file", required=True,
                        help="Position file of the drone: RTKLIB/Emlid .pos or SkyHub position.csv")
    args = parser.parse_args()

    path = Path(args.file)
    if not path.is_file():
        raise SystemExit(f"File does not exist: {path}")
    if not args.position_file.strip():
        raise SystemExit("Choose the position file: a PPK .pos or the flight position.csv")
    position_path = Path(args.position_file)
    if not position_path.is_file():
        raise SystemExit(f"Position file does not exist: {position_path}")

    print(f"Drone positions from {position_path.name}")
    try:
        recalculate(path, position_path)
    except ValueError as e:
        raise SystemExit(f"Cannot read {path.name}: {e}")


if __name__ == "__main__":
    sys.exit(main())
