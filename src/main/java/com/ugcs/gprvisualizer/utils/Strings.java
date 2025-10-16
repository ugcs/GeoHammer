package com.ugcs.gprvisualizer.utils;

public final class Strings {

	private static final String EMPTY_STRING = "";

	private Strings() {
	}

	public static String empty() {
		return EMPTY_STRING;
	}

	public static boolean isNullOrEmpty(String str) {
		return str == null || str.isEmpty();
	}

	public static boolean isNullOrBlank(String str) {
		return str == null || str.isBlank();
	}

	public static String nullToEmpty(String str) {
		return str == null ? EMPTY_STRING : str;
	}

	public static String emptyToNull(String str) {
		return str != null && str.isEmpty() ? null : str;
	}

	public static String trim(String str) {
		return str != null ? str.trim() : str;
	}

	public static String removeSpaces(String str) {
		if (str == null)
			return null;
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (!Character.isWhitespace(c))
				sb.append(c);
		}
		return sb.toString();
	}

	public static String toLowerCase(String str) {
		return str != null ? str.toLowerCase() : str;
	}

	public static String toUpperCase(String str) {
		return str != null ? str.toUpperCase() : str;
	}

	public static boolean equalsIgnoreCase(String a, String b) {
		return Strings.isNullOrEmpty(a) ? Strings.isNullOrEmpty(b) : a.equalsIgnoreCase(b);
	}

	public static String mask(String str, int visibleAtStartAndEnd) {
		return mask(str, visibleAtStartAndEnd, visibleAtStartAndEnd);
	}

	public static String mask(String str, int visibleAtStart, int visibleAtEnd) {
		if (Strings.isNullOrEmpty(str) || str.length() == 1)
			return "***";

		visibleAtStart = Math.max(0, visibleAtStart);
		visibleAtEnd = Math.max(0, visibleAtEnd);

		return str.length() > visibleAtStart + visibleAtEnd
				? str.substring(0, visibleAtStart) + "***" + str.substring(str.length() - visibleAtEnd)
				: "***" + str.charAt(str.length() - 1);
	}
}
