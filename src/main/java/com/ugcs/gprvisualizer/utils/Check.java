package com.ugcs.gprvisualizer.utils;

import java.util.Collection;

public final class Check {
	
	private Check() {
	}
	
	public static <T> T notNull(T reference) {
		return notNull(reference, null);
	}

	public static <T> T notNull(T reference, String message) {
		if (reference == null) {
			throw new NullPointerException(message != null
					? message
					: "Object reference is null");
		}
		return reference;
	}

	public static String notEmpty(String str) {
		return notEmpty(str, null);
	}

	public static String notEmpty(String str, String message) {
		if (str == null || str.isEmpty()) {
			throw new IllegalArgumentException(message != null
					? message
					: "String is empty");
		}
		return str;
	}

	public static <T> Collection<T> notEmpty(Collection<T> collection) {
		return notEmpty(collection, null);
	}

	public static <T> Collection<T> notEmpty(Collection<T> collection, String message) {
		notNull(collection, message);
		condition(!collection.isEmpty(), message);
		return collection;
	}

	public static void condition(boolean condition) {
		condition(condition, null);
	}

	public static void condition(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message != null
					? message
					: "Condition violated");
		}
	}

	public static int indexInBounds(int index, int length) {
		return indexInBounds(index, length, null);
	}

	public static int indexInBounds(int index, int length, String message) {
		if (index < 0 || index >= length) {
			throw new IndexOutOfBoundsException(message != null
					? message
					: "Index " + index + " is out of bounds [0, " + length + ")");
		}
		return index;
	}

	public static void rangeInBounds(int rangeOffset, int rangeLength, int length) {
		rangeInBounds(rangeOffset, rangeLength, length, null);
	}

	public static void rangeInBounds(int rangeOffset, int rangeLength, int length, String message) {
		if (rangeOffset < 0 || rangeLength < 0 || rangeOffset + rangeLength > length) {
			throw new IndexOutOfBoundsException(message != null
					? message
					: "Range [" + rangeOffset + ", " + (rangeOffset + rangeLength)
							+ ") is out of bounds [0, " + length + ")");
		}
	}
}
