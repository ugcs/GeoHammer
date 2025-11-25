package com.ugcs.geohammer.geotagger;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.Model;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class CoverageStatusResolverTest {

	private FileManager fileManager;

	@BeforeEach
	void setUp() {
		mock(Model.class);
		fileManager = mock(FileManager.class);
	}

	@Test
	void resolve_shouldReturnNotCovered_whenDataFileIsNull() {
		List<SgyFile> positionFiles = List.of(createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00"));

		CoverageStatus result = CoverageStatusResolver.resolve(positionFiles, null);

		assertEquals(CoverageStatus.NotCovered, result);
	}

	@Test
	void resolve_shouldReturnNotCovered_whenPositionFilesIsNull() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(null, dataFile);

		assertEquals(CoverageStatus.NotCovered, result);
	}

	@Test
	void resolve_shouldReturnNotCovered_whenPositionFilesIsEmpty() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(Collections.emptyList(), dataFile);

		assertEquals(CoverageStatus.NotCovered, result);
	}

	@Test
	void resolve_shouldReturnNull_whenDataFileHasNoStartTime() {
		SgyFile dataFile = createSgyFileWithNoDateTime();
		List<SgyFile> positionFiles = List.of(createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00"));

		CoverageStatus result = CoverageStatusResolver.resolve(positionFiles, dataFile);

		assertNull(result);
	}

	@Test
	void resolve_shouldReturnFullyCovered_whenPositionFileCoversDataFileCompletely() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T09:00:00", "2024-01-01T12:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(List.of(positionFile), dataFile);

		assertEquals(CoverageStatus.FullyCovered, result);
	}

	@Test
	void resolve_shouldReturnFullyCovered_whenDataFileAndPositionFileHaveSameTimeRange() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(List.of(positionFile), dataFile);

		assertEquals(CoverageStatus.FullyCovered, result);
	}

	@Test
	void resolve_shouldReturnPartiallyCovered_whenPositionFileCoversOnlyStart() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T12:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T09:00:00", "2024-01-01T11:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(List.of(positionFile), dataFile);

		assertEquals(CoverageStatus.PartiallyCovered, result);
	}

	@Test
	void resolve_shouldReturnPartiallyCovered_whenPositionFileCoversOnlyEnd() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T12:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T11:00:00", "2024-01-01T13:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(List.of(positionFile), dataFile);

		assertEquals(CoverageStatus.PartiallyCovered, result);
	}

	@Test
	void resolve_shouldReturnPartiallyCovered_whenPositionFileIsWithinDataFile() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T14:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T11:00:00", "2024-01-01T12:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(List.of(positionFile), dataFile);

		assertEquals(CoverageStatus.PartiallyCovered, result);
	}

	@Test
	void resolve_shouldReturnNotCovered_whenPositionFileDoesNotOverlap() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFile = createSgyFile("2024-01-01T12:00:00", "2024-01-01T13:00:00");

		CoverageStatus result = CoverageStatusResolver.resolve(List.of(positionFile), dataFile);

		assertEquals(CoverageStatus.NotCovered, result);
	}

	@Test
	void resolve_shouldIgnorePositionFilesWithNoDateTime() {
		SgyFile dataFile = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFileValid = createSgyFile("2024-01-01T10:00:00", "2024-01-01T11:00:00");
		SgyFile positionFileInvalid = createSgyFileWithNoDateTime();

		CoverageStatus result = CoverageStatusResolver.resolve(List.of(positionFileValid, positionFileInvalid), dataFile);

		assertEquals(CoverageStatus.FullyCovered, result);
	}

	private SgyFile createSgyFile(String startDateTime, String endDateTime) {
		CsvFile file = new CsvFile(fileManager.getFileTemplates());
		ColumnSchema columnSchema = new ColumnSchema();
		GeoData startData = new GeoData(columnSchema);
		startData.setDateTime(LocalDateTime.parse(startDateTime));

		GeoData endData = new GeoData(columnSchema);
		endData.setDateTime(LocalDateTime.parse(endDateTime));

		List<GeoData> geoData = file.getGeoData();
		geoData.add(startData);
		geoData.add(endData);
		file.setGeoData(geoData);

		return file;
	}

	private SgyFile createSgyFileWithNoDateTime() {
		CsvFile file = new CsvFile(fileManager.getFileTemplates());
		ColumnSchema columnSchema = new ColumnSchema();
		GeoData data = new GeoData(columnSchema);
		List<GeoData> geoData = file.getGeoData();
		geoData.add(data);
		file.setGeoData(geoData);
		return file;
	}
}
