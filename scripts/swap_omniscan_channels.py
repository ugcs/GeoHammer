#!/usr/bin/env python3
"""
Swap Omniscan 450 port/starboard channels in an SVLOG file, IN PLACE.

Each side-scan ping is a packet with id == 2198 whose transducer look-angle
is a single IEEE-754 float at payload offset 44, equal to +90.0 (starboard)
or -90.0 (port). Flipping that sign relabels the ping to the opposite side.

Only that one field is touched, so there is no risk of corrupting raw sample
data that might coincidentally contain the +/-90.0 byte pattern.

Packet layout:
  0-1 : b'BR' magic
  2-3 : u16 payload length (N)
  4-5 : u16 packet_id
  6-7 : u8 reserved x2
  8 .. 8+N-1   : payload
  8+N .. 8+N+1 : u16 checksum = sum(all bytes 0..8+N-1) & 0xFFFF
"""
import argparse
import struct
from pathlib import Path

HEADER_SIZE = 8           # 2s + H + H + B + B
PING_PACKET_ID = 2198
ANGLE_OFFSET = 44         # offset of the +/-90.0 float within the payload

PATTERN_POS_90 = b"\x00\x00\xb4\x42"   # +90.0f, little-endian
PATTERN_NEG_90 = b"\x00\x00\xb4\xc2"   # -90.0f, little-endian


def swap_channels_in_place(path: Path) -> None:
    data = bytearray(path.read_bytes())

    i = 0
    packet_count = 0
    swapped = 0

    while i < len(data):
        if i + HEADER_SIZE > len(data):
            print(f"Truncated header at packet {packet_count}, stopping.")
            break

        magic = bytes(data[i:i + 2])
        if magic != b"BR":
            raise ValueError(
                f"Sync lost at packet {packet_count}: expected b'BR', got {magic!r}"
            )

        payload_len, packet_id = struct.unpack_from("<HH", data, i + 2)
        payload_start = i + HEADER_SIZE
        checksum_start = payload_start + payload_len

        if checksum_start + 2 > len(data):
            print(f"Truncated payload/checksum at packet {packet_count}, stopping.")
            break

        if packet_id == PING_PACKET_ID and payload_len >= ANGLE_OFFSET + 4:
            a = payload_start + ANGLE_OFFSET
            angle = bytes(data[a:a + 4])
            if angle == PATTERN_POS_90:
                data[a:a + 4] = PATTERN_NEG_90
            elif angle == PATTERN_NEG_90:
                data[a:a + 4] = PATTERN_POS_90
            else:
                angle = None

            if angle is not None:
                new_checksum = sum(data[i:checksum_start]) & 0xFFFF
                struct.pack_into("<H", data, checksum_start, new_checksum)
                swapped += 1

        i = checksum_start + 2
        packet_count += 1

    path.write_bytes(data)
    print(f"Processed {packet_count} packets.")
    print(f"Swapped {swapped} ping(s) (port <-> starboard) in {path.name}.")


def main():
    parser = argparse.ArgumentParser(
        description="Swap Omniscan 450 port/starboard channels in an SVLOG file, in place."
    )
    parser.add_argument("file", help="SVLOG file to modify in place")
    args = parser.parse_args()

    path = Path(args.file)
    if not path.is_file():
        raise SystemExit(f"File does not exist: {path}")

    print(f"Editing in place: {path}")
    swap_channels_in_place(path)


if __name__ == "__main__":
    main()
