package com.ugcs.geohammer.format;

import java.io.File;

import org.jspecify.annotations.Nullable;

public class FileLockedException extends IllegalStateException {

	public FileLockedException(@Nullable File file) {
		super("File is locked, retry later"
				+ (file != null ? ": " + file.getName() : ""));
	}
}
