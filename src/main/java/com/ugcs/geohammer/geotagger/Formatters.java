package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.util.Templates;

public final class Formatters {

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS");
	private static final String DEFAULT_VALUE = "-";

	public static String formatFileName(SgyFile sgyFile) {
		File file = sgyFile.getFile();
		if (file == null) {
			return DEFAULT_VALUE;
		}
		return file.getName();
	}

	public static String formatTemplateName(SgyFile file) {
		String templateName = Templates.getTemplateName(file);
		if (templateName == null) {
			return DEFAULT_VALUE;
		}
		return templateName;
	}

	public static String formatDateTime(Instant time) {
		if (time == null) {
			return DEFAULT_VALUE;
		}
		return LocalDateTime.ofInstant(time, ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
	}
}
