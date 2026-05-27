package com.ugcs.geohammer.format.csv.parser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ugcs.geohammer.util.IncorrectFormatException;

public class Warnings {
	private final Map<String, Group> warnings = new LinkedHashMap<>();

	public void add(String column, IncorrectFormatException e) {
		String key = e.getFormat() != null ? column + ":" + e.getFormat() : column;
		add(key, column, e.getMessage());
	}

	public void add(String key, String column, String message) {
		warnings.compute(key, (k, existing) -> {
			if (existing == null) {
				return new Group(column, message);
			}
			existing.incrementCount();
			return existing;
		});
	}

	public Collection<Group> getGroups() {
		return warnings.values();
	}

	public static class Group {

		private final String column;

		private final String message;

		private int count;

		public Group(String column, String message) {
			this.column = column;
			this.message = message;
			count = 1;
		}

		private void incrementCount() {
			this.count++;
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

		@Override
		public String toString() {
			return column + " (" + count + (count == 1 ? " error): " : " errors): ") + message;
		}
	}
}
