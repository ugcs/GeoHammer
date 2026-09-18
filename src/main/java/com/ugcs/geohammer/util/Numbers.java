package com.ugcs.geohammer.util;

import ch.randelshofer.fastdoubleparser.JavaDoubleParser;

public final class Numbers {

	// every integer up to 2^53 in magnitude converts to double exactly
	private static final long MAX_EXACT_MANTISSA = 1L << 53;

	// exact powers of ten for the fast decimal path; 1e22 is the largest exactly representable power
	private static final double[] POW10 = {
			1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
			1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
	};

	private Numbers() {
	}

	public static ParseResult parseNumber(CharSequence str) {
		if (str == null) {
			return ParseResult.empty();
		}
		return parseNumber(str, 0, str.length());
	}

	public static ParseResult parseNumber(CharSequence str, int from, int to) {
		if (str == null) {
			return ParseResult.empty();
		}
		while (from < to && str.charAt(from) <= ' ') {
			from++;
		}
		while (to > from && str.charAt(to - 1) <= ' ') {
			to--;
		}
		if (from == to) {
			return ParseResult.empty();
		}

		int start = from; // fallback re-parses from here, sign included
		boolean negative = false;
		char c = str.charAt(from);
		if (c == '-' || c == '+') {
			negative = c == '-';
			from++;
		}

		long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
		long limit10 = limit / 10;

		long mantissa = 0; // accumulated negatively to avoid overflow near Long.MIN_VALUE
		int numFractionDigits = -1; // -1 until the separator is seen
		int numDigits = 0;

		for (int i = from; i < to; i++) {
			c = str.charAt(i);
			if (c >= '0' && c <= '9') {
				int digit = c - '0';
				if (mantissa < limit10) {
					// x10 would overflow mantissa
					return parseFallback(str, start, to);
				}
				mantissa *= 10;
				if (mantissa < limit + digit) {
					// -digit would overflow mantissa
					return parseFallback(str, start, to);
				}
				mantissa -= digit;

				numDigits++;
				if (numFractionDigits >= 0) {
					numFractionDigits++;
				}
			} else if (c == '.') {
				if (numFractionDigits >= 0) {
					return ParseResult.error();
				}
				numFractionDigits = 0;
			} else if (c == 'e' || c == 'E') {
				return parseFallback(str, start, to);
			} else {
				return ParseResult.error();
			}
		}

		if (numDigits == 0) {
			// filters out NaN, Infinity, -Infinity, and +Infinity;
			// but they would be cut anyway by decimal separator criteria
			return ParseResult.error();
		}

		if (numFractionDigits < 0) {
			// integral
			long value = negative ? mantissa : -mantissa;
			return value == (int) value
					? ParseResult.number((int) value)
					: ParseResult.number(value);
		} else {
			// fp
			if (mantissa >= -MAX_EXACT_MANTISSA && numFractionDigits < POW10.length) {
				// both operands are exact doubles here, so the division rounds once
				double magnitude = (double) -mantissa / POW10[numFractionDigits];
				return ParseResult.number(negative ? -magnitude : magnitude); // keeps -0.0
			} else {
				return parseFallback(str, start, to);
			}
		}
	}

	private static ParseResult parseFallback(CharSequence s, int from, int to) {
		try {
			return ParseResult.number(JavaDoubleParser.parseDouble(s, from, to - from));
		} catch (NumberFormatException e) {
			return ParseResult.error();
		}
	}

	public record ParseResult(Number number, boolean valid) {

		public static ParseResult empty() {
			return number(null);
		}

		public static ParseResult number(Number number) {
			return new ParseResult(number, true);
		}

		public static ParseResult error() {
			return new ParseResult(null, false);
		}
	}
}
