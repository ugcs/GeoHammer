package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.domain.TimeRange;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.template.Template;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class SgyFileInfoExtractor {

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
	private static final String DEFAULT_VALUE = "-";

	private final FileManager fileManager;

	public SgyFileInfoExtractor(FileManager fileManager) {
		this.fileManager = fileManager;
	}

	public SgyFileInfo extractInfo(@Nullable SgyFile sgyFile) {
		if (sgyFile == null || sgyFile.getFile() == null) {
			return SgyFileInfo.empty();
		}

		File file = sgyFile.getFile();
		String fileName = file.getName();
		String templateName = extractTemplateName(file);
		TimeInfo timeInfo = extractTimeInfo(sgyFile);
		return new SgyFileInfo(fileName, templateName, timeInfo.startTime, timeInfo.endTime);
	}

	private String extractTemplateName(File file) {
		Template template = fileManager.getFileTemplates().findTemplate(
				fileManager.getFileTemplates().getTemplates(),
				file
		);
		return template != null ? template.getName() : DEFAULT_VALUE;
	}

	private TimeInfo extractTimeInfo(SgyFile sgyFile) {
		List<GeoData> geoData = sgyFile.getGeoData();
		if (geoData == null || geoData.isEmpty()) {
			return TimeInfo.empty();
		}

		TimeRange timeRange = CoverageStatusResolver.toTimeRange(sgyFile);
		if (timeRange == null) {
			return TimeInfo.empty();
		}

		String startTime = LocalDateTime.ofInstant(timeRange.start(), ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
		String endTime = LocalDateTime.ofInstant(timeRange.end(), ZoneOffset.UTC).format(DATE_TIME_FORMATTER);

		return new TimeInfo(startTime, endTime);
	}

	public record SgyFileInfo(String fileName, String templateName, String startTime, String endTime) {

		public static SgyFileInfo empty() {
			return new SgyFileInfo(DEFAULT_VALUE, DEFAULT_VALUE, DEFAULT_VALUE, DEFAULT_VALUE);
		}


	}

	private record TimeInfo(String startTime, String endTime) {
		static TimeInfo empty() {
			return new TimeInfo(DEFAULT_VALUE, DEFAULT_VALUE);
		}
	}
}
