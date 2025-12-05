package com.ugcs.geohammer.geotagger;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.geotagger.domain.CoverageStatus;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.Model;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class CoverageStatusTest {

	private FileManager fileManager;

	@BeforeEach
	void setUp() {
		mock(Model.class);
		fileManager = mock(FileManager.class);
	}

	@Test
	void compute_returnNotCovered_whenDataFileIsNull() {
		List<SgyFile> positionFiles = List.of(createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00"));

		CoverageStatus result = CoverageStatus.compute(null, positionFiles);

		assertEquals(CoverageStatus.NOT_COVERED, result);
	}

	@Test
	void compute_returnNotCovered_whenPositionFilesIsNull() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, null);

		assertEquals(CoverageStatus.NOT_COVERED, result);
	}

	@Test
	void compute_returnNotCovered_whenPositionFilesIsEmpty() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, Collections.emptyList());

		assertEquals(CoverageStatus.NOT_COVERED, result);
	}

	@Test
	void compute_returnNull_whenDataFileHasNoStartTime() {
		SgyFile dataFile = createSgyFileWithNoDateTime();
		List<SgyFile> positionFiles = List.of(createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00"));

		CoverageStatus result = CoverageStatus.compute(dataFile, positionFiles);

		assertEquals(CoverageStatus.NOT_COVERED, result);
	}

	@Test
	void compute_returnFullyCovered_whenPositionFileCoversDataFileCompletely() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T09:00:00", "2024-01-01T12:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile));

		assertEquals(CoverageStatus.FULLY_COVERED, result);
	}

	@Test
	void compute_returnFullyCovered_whenDataFileAndPositionFileHaveSameTimeRange() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile));

		assertEquals(CoverageStatus.FULLY_COVERED, result);
	}

	@Test
	void compute_returnFullyCovered_whenMultiplePositionFilesCoverDataFileWithoutGaps() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T15:00:00");
		SgyFile positionFile1 = createSgyFile("2024-01-01T09:00:00", "2024-01-01T12:00:00");
		SgyFile positionFile2 = createSgyFile("2024-01-01T12:00:00", "2024-01-01T16:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile1, positionFile2));

		assertEquals(CoverageStatus.FULLY_COVERED, result);
	}

	@Test
	void compute_returnFullyCovered_whenMultipleOverlappingPositionFilesCoverDataFile() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T15:00:00");
		SgyFile positionFile1 = createSgyFile("2024-01-01T09:00:00", "2024-01-01T13:00:00");
		SgyFile positionFile2 = createSgyFile("2024-01-01T12:00:00", "2024-01-01T16:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile1, positionFile2));

		assertEquals(CoverageStatus.FULLY_COVERED, result);
	}

	@Test
	void compute_returnPartiallyCovered_whenPositionFileCoversOnlyStart() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T12:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T09:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile));

		assertEquals(CoverageStatus.PARTIALLY_COVERED, result);
	}

	@Test
	void compute_returnPartiallyCovered_whenPositionFileCoversOnlyEnd() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T12:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T11:00:00", "2024-01-01T13:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile));

		assertEquals(CoverageStatus.PARTIALLY_COVERED, result);
	}

	@Test
	void compute_returnPartiallyCovered_whenPositionFileIsWithinDataFile() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T14:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T11:00:00", "2024-01-01T12:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile));

		assertEquals(CoverageStatus.PARTIALLY_COVERED, result);
	}

	@Test
	void compute_returnPartiallyCovered_whenMultiplePositionFilesHaveGap() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T15:00:00");
		SgyFile positionFile1 = createSgyFile("2024-01-01T09:00:00", "2024-01-01T12:00:00");
		SgyFile positionFile2 = createSgyFile("2024-01-01T13:00:00", "2024-01-01T16:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile1, positionFile2));

		assertEquals(CoverageStatus.PARTIALLY_COVERED, result);
	}

	@Test
	void compute_returnNotCovered_whenPositionFilesDoNotOverlap() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile1 = createSgyFile("2024-01-01T11:00:00", "2024-01-01T13:00:00");
		SgyFile positionFile2 = createSgyFile("2024-01-01T09:00:00", "2024-01-01T10:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile1, positionFile2));

		assertEquals(CoverageStatus.PARTIALLY_COVERED, result);
	}

	@Test
	void compute_returnNotCovered_whenPositionFilesAreBeforeDataFile() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T08:00:00", "2024-01-01T09:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile));

		assertEquals(CoverageStatus.NOT_COVERED, result);
	}

	@Test
	void compute_returnNotCovered_whenPositionFilesAreAfterDataFile() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T12:00:00", "2024-01-01T13:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile));

		assertEquals(CoverageStatus.NOT_COVERED, result);
	}

	@Test
	void compute_shouldIgnorePositionFilesWithNoDateTime() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFileValid = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFileInvalid = createSgyFileWithNoDateTime();

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFileValid, positionFileInvalid));

		assertEquals(CoverageStatus.FULLY_COVERED, result);
	}

	@Test
	void compute_returnPartiallyCovered_whenPositionFilesTouchAtBoundaries() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T15:00:00");
		SgyFile positionFile1 = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile2 = createSgyFile("2024-01-01T14:00:00", "2024-01-01T15:00:00");

		CoverageStatus result = CoverageStatus.compute(dataFile, List.of(positionFile1, positionFile2));

		assertEquals(CoverageStatus.PARTIALLY_COVERED, result);
	}

	private SgyFile createSgyFile(String startDateTime, String endDateTime) {
		CsvFile file = new CsvFile(fileManager.getFileTemplates());
		ColumnSchema columnSchema = new ColumnSchema();
		GeoData startData = new GeoData(columnSchema);
		startData.setDateTime(LocalDateTime.parse(startDateTime));

		GeoData endData = new GeoData(columnSchema);
		endData.setDateTime(LocalDateTime.parse(endDateTime));

		List<GeoData> geoData = List.of(startData, endData);
		file.setGeoData(geoData);

		return file;
	}

	private SgyFile createSgyFileWithNoDateTime() {
		CsvFile file = new CsvFile(fileManager.getFileTemplates());
		ColumnSchema columnSchema = new ColumnSchema();
		GeoData data = new GeoData(columnSchema);
		file.setGeoData(List.of(data));
		return file;
	}
}

