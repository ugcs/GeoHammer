package com.ugcs.geohammer.format.csv.parser;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ugcs.geohammer.util.Strings;

public final class ParseWarnings {

	private final Map<String, ParseWarning> warnings = new LinkedHashMap<>();

	public void addFormatError(String column, String value, String format) {
		if (Strings.isNullOrBlank(value)) {
			return;
		}
		String message = "Cannot parse '" + value + "' with format '" + format + "'";
		add(column, format, message);
	}

	public void addNumberError(String column, String value) {
		if (Strings.isNullOrBlank(value)) {
			return;
		}
		String message = "Cannot parse '" + value + "' as a number";
		add(column, "", message);
	}

	private void add(String column, String format, String message) {
		String key = column + "-" + format;
		ParseWarning existing = warnings.get(key);
		if (existing == null) {
			warnings.put(key, new ParseWarning(column, message));
		} else {
			existing.incrementCount();
		}
	}

	public Collection<ParseWarning> all() {
		return Collections.unmodifiableCollection(warnings.values());
	}

	public boolean isEmpty() {
		return warnings.isEmpty();
	}

	public String format() {
		StringBuilder sb = new StringBuilder();
		for (ParseWarning warning : warnings.values()) {
			if (!sb.isEmpty()) {
				sb.append("\n");
			}
			sb.append(warning.getColumn())
					.append(": ")
					.append(warning.getMessage())
					.append(" (occurrences: ")
					.append(warning.getCount())
					.append(")");
		}
		return sb.toString();
	}

	public static final class ParseWarning {

		private final String column;

		private final String message;

		private int count;

		ParseWarning(String column, String message) {
			this.column = column;
			this.message = message;
			this.count = 1;
		}

		void incrementCount() {
			count++;
		}

		public String getColumn() {
			return column;
		}

		public String getMessage() {
			return message;
		}

		public int getCount() {
			return count;
		}
	}
}
