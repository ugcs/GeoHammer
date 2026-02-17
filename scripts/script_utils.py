import re


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
