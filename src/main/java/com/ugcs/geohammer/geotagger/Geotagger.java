package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.geotagger.domain.Position;
import com.ugcs.geohammer.geotagger.extraction.PositionExtractor;
import com.ugcs.geohammer.geotagger.extraction.SgyPositionExtractor;
import com.ugcs.geohammer.geotagger.writer.CsvPositionWriter;
import com.ugcs.geohammer.geotagger.writer.PositionWriter;
import com.ugcs.geohammer.geotagger.writer.TracePositionWriter;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.FileTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class Geotagger {

	private final PositionExtractor positionExtractor = new SgyPositionExtractor();

	public CompletableFuture<String> updateCoordinates(List<SgyFile> positionFiles,
													   List<SgyFile> dataFiles,
													   Consumer<Integer> progressCallback) {
		return CompletableFuture.supplyAsync(() -> {
			List<Position> positions = positionExtractor.extractFrom(positionFiles);

			int totalRows = dataFiles.stream()
					.mapToInt(sgyFile -> sgyFile.getGeoData().size())
					.sum();
			ProgressState progressState = new ProgressState(totalRows, progressCallback);
			try {
				for (SgyFile dataFile : dataFiles) {
					interpolateAndUpdatePositions(positions, dataFile, progressState);
					writeToFile(dataFile);
				}

				return "Processing completed successfully.";
			} catch (Exception e) {
				return e.getMessage();
			}
		});
	}

	private void writeToFile(SgyFile file) {
		PositionWriter writer = getWriterForFile(file);
		writer.writePositions(file);
	}

	private PositionWriter getWriterForFile(SgyFile file) {
		if (file instanceof TraceFile) {
			return new TracePositionWriter();
		} else if (file instanceof CsvFile) {
			return new CsvPositionWriter();
		}
		throw new IllegalArgumentException("No writer available for file type: " + file.getClass().getName());
	}

	private void interpolateAndUpdatePositions(
			List<Position> referencePositions,
			SgyFile dataFile,
			ProgressState progressState
	) throws IllegalStateException {
		List<Position> sortedPositions = sortByTime(referencePositions);
		List<Position> dataFilePositions = positionExtractor.extractFrom(dataFile);

		for (Position dataPoint : dataFilePositions) {
			interpolateAndUpdate(dataPoint, sortedPositions);
			progressState.incrementProcessed();
		}
	}

	private List<Position> sortByTime(List<Position> positions) {
		return positions.stream()
				.sorted(Comparator.comparingLong(Position::getTimeMs))
				.toList();
	}

	private void interpolateAndUpdate(Position dataPoint, List<Position> sortedPositions) throws IllegalStateException {
		long targetTime = dataPoint.getTimeMs();

		int leftIdx = findLeftIndex(sortedPositions, targetTime);
		int rightIdx = findRightIndex(sortedPositions, targetTime);

		if (leftIdx >= 0 && rightIdx >= 0 ) {
			Position left = sortedPositions.get(leftIdx);
			Position right = sortedPositions.get(rightIdx);
			dataPoint.interpolateCoordinatesFrom(left, right, targetTime);
		}

		if (leftIdx < 0 || rightIdx < 0) {
			throw new IllegalStateException("Cannot interpolate position for time: " + targetTime);
		}
	}

	private int findLeftIndex(List<Position> sortedPositions, long targetTime) {
		int index = Collections.binarySearch(sortedPositions,
				new Position(targetTime, 0.0, 0.0, 0.0),
				Comparator.comparingLong(Position::getTimeMs));

		if (index >= 0) {
			return index;
		} else {
			int insertionPoint = -(index + 1);
			return insertionPoint - 1;
		}
	}

	private int findRightIndex(List<Position> sortedPositions, long targetTime) {
		int index = Collections.binarySearch(sortedPositions,
				new Position(targetTime, 0.0, 0.0, 0.0),
				Comparator.comparingLong(Position::getTimeMs));

		if (index >= 0) {
			return index + 1 < sortedPositions.size() ? index + 1 : -1;
		} else {
			int insertionPoint = -(index + 1);
			return insertionPoint < sortedPositions.size() ? insertionPoint : -1;
		}
	}

	@Nullable
	public SgyFile createSgyFile(File file, FileTemplates fileTemplates) throws IOException {
		if (FileTypes.isGprFile(file)) {
			GprFile sgyFile = new GprFile();
			sgyFile.open(file);
			return sgyFile;
		} else if (FileTypes.isCsvFile(file)) {
			CsvFile sgyFile = new CsvFile(fileTemplates);
			sgyFile.open(file);
			return sgyFile;
		} else {
			return null;
		}
	}
}
