import os
import re
import csv

# GeoHammer passes the header of each semantic column in the environment
# (e.g. GEOHAMMER_COLUMN_LATITUDE=lat), as templates name the columns differently.
# Must match ScriptExecutor.COLUMN_VARIABLE_PREFIX on the Java side.
COLUMN_VARIABLE_PREFIX = "GEOHAMMER_COLUMN_"

# Semantic names, also the default headers when run outside of GeoHammer.
LATITUDE = "Latitude"
LONGITUDE = "Longitude"
LINE = "Line"

# Bytes read from the file start for csv.Sniffer to infer the delimiter (64 KiB,
# enough for the header plus many data rows; avoids reading large files whole).
SNIFF_SAMPLE_BYTES = 64 * 1024


def detect_separator(input_path, default=","):
    # Infer the delimiter from the file content itself: csv.Sniffer builds a
    # per-character frequency table over a sample and picks the one giving a
    # consistent column count across rows - no fixed candidate list. Raises
    # csv.Error only when there is no delimiter (single-column file), where the
    # separator is irrelevant and the default is fine.
    try:
        with open(input_path, "r", encoding="utf-8-sig", errors="replace") as f:
            sample = f.read(SNIFF_SAMPLE_BYTES)
    except OSError:
        return default
    if not sample.strip():
        return default
    try:
        return csv.Sniffer().sniff(sample).delimiter
    except csv.Error:
        return default


def semantic_column(semantic):
    key = COLUMN_VARIABLE_PREFIX + semantic.upper().replace(" ", "_")
    return os.environ.get(key) or semantic


def normalize_input_stem(stem):
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
