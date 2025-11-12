package com.ugcs.geohammer.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class Nulls {

	@SuppressWarnings("rawtypes")
	private static final Iterable EMPTY_ITERABLE = new EmptyIterable<>();

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

	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> toEmpty(Iterable<T> iterable) {
		return iterable == null ? (Iterable<T>)EMPTY_ITERABLE : iterable;
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

	static class EmptyIterator<T> implements Iterator<T> {

		@Override
		public boolean hasNext() {
			return false;
		}

		@Override
		public T next() {
			return null;
		}
	}

	static class EmptyIterable<T> implements Iterable<T> {

		@Override
		public @NotNull Iterator<T> iterator() {
			return new EmptyIterator<>();
		}
	}
}
