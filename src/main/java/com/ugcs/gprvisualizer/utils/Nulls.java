package com.ugcs.gprvisualizer.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class Nulls {

	private Nulls() {
	}

	public static String toEmpty(String str) {
		return Strings.nullToEmpty(str);
	}

	public static <T> List<T> toEmpty(List<T> list) {
		return list == null ? Collections.emptyList() : list;
	}

	public static <T> Set<T> toEmpty(Set<T> set) {
		return set == null ? Collections.emptySet() : set;
	}

	public static <K, V> Map<K, V> toEmpty(Map<K, V> map) {
		return map == null ? Collections.emptyMap() : map;
	}

	public static <T> boolean isNullOrEmpty(Collection<T> collection) {
		return collection == null || collection.isEmpty();
	}

	public static <K, V> boolean isNullOrEmpty(Map<K, V> map) {
		return map == null || map.isEmpty();
	}

	public static <T> void ifPresent(T value, Consumer<T> consumer) {
		if (value != null) {
			consumer.accept(value);
		}
	}
}
