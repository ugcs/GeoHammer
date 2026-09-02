package com.ugcs.geohammer.format.csv.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ugcs.geohammer.util.IncorrectFormatException;
import com.ugcs.geohammer.util.Strings;

public class Warnings {

	private final List<String> warnings = new ArrayList<>();

	private final Map<String, FormatError> formatErrors = new LinkedHashMap<>();

	public boolean isEmpty() {
		return warnings.isEmpty() && formatErrors.isEmpty();
	}

	public void addWarning(String message) {
		if (Strings.isNullOrEmpty(message)) {
			return;
		}
		warnings.add(message);
	}

	public void addFormatError(String column, IncorrectFormatException e) {
		String key = e.getFormat() != null ? column + ":" + e.getFormat() : column;
		addFormatError(key, column, e.getMessage());
	}

	private void addFormatError(String key, String column, String message) {
		formatErrors.compute(key, (k, group) -> {
			if (group == null) {
				return new FormatError(column, message);
			}
			group.incrementCount();
			return group;
		});
	}

	public String format() {
		StringBuilder sb = new StringBuilder();
		for (String warning : warnings) {
			if (!sb.isEmpty()) {
				sb.append("\n\n");
			}
			sb.append(warning);
		}
		if (!formatErrors.isEmpty()) {
			if (!sb.isEmpty()) {
				sb.append("\n\n");
			}
			sb.append("The following values could not be parsed and are left empty:");
			for (Warnings.FormatError formatError : formatErrors.values()) {
				sb.append("\n").append(formatError);
			}
		}
		return sb.toString();
	}

	public static class FormatError {

		private final String column;

		private final String message;

		private int count;

		public FormatError(String column, String message) {
			this.column = column;
			this.message = message;
			count = 1;
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

		private void incrementCount() {
			this.count++;
		}

		@Override
		public String toString() {
			return column + " (" + count + (count == 1 ? " error): " : " errors): ") + message;
		}
	}
}
