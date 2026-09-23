package com.ugcs.geohammer.util;

import org.jspecify.annotations.Nullable;

public record Result<T>(T value, Exception error) {

	public boolean isSuccess() {
		return error == null;
	}

	public boolean isError() {
		return !isSuccess();
	}

	public static <T> Result<T> success(@Nullable T value) {
		return new Result<>(value, null);
	}

	public static <T> Result<T> error(Exception error) {
		return new Result<>(null, Check.notNull(error));
	}
}