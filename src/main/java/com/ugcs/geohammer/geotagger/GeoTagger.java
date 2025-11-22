package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.LinearInterpolator;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.FileTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GeoTagger {

	private static final int MAX_TIME_DIFFERENCE_MS = 1000;
	private static final Logger log = LoggerFactory.getLogger(GeoTagger.class);

	private final Model model;

	public GeoTagger(Model model) {
		this.model = model;
	}

	public CompletableFuture<String> updateCoordinates(List<SgyFile> positionFiles, List<SgyFile> dataFiles,
													   Consumer<Integer> progressCallback) {
		return CompletableFuture.supplyAsync(() -> {
			List<Position> positions;
			try {
				positions = getPositions(positionFiles);
			} catch (IOException e) {
				return e.getMessage();
			}

			int totalRows = dataFiles.stream()
					.mapToInt(f -> getPositions(f).size())
					.sum();
			if (totalRows == 0) {
				progressCallback.accept(100);
				return "No data rows to process.";
			}

			AtomicInteger processedRows = new AtomicInteger(0);

			for (SgyFile dataFile : dataFiles) {
				try {
					List<Position> interpolatedPositions = interpolatePositions(dataFile, positions, processedRows,
							totalRows, progressCallback);
					writeToFile(dataFile, interpolatedPositions);
				} catch (IOException e) {
					return e.getMessage();
				}
			}

			progressCallback.accept(100);
			return "Processing completed successfully.";
		});
	}

	protected SgyFile toSgyFile(File file) throws IOException {
		SgyFile modelFile = model.getFileManager().getFile(file);
		if (modelFile != null) {
			return modelFile;
		} else {
			if (FileTypes.isGprFile(file)) {
				GprFile sgyFile = new GprFile();
				sgyFile.open(file);
				model.getFileManager().addFile(sgyFile);
				return sgyFile;
			} else if (FileTypes.isCsvFile(file)) {
				CsvFile sgyFile = new CsvFile(model.getFileManager().getFileTemplates());
				sgyFile.open(file);
				model.getFileManager().addFile(sgyFile);
				return sgyFile;
			} else {
				return null;
			}
		}
	}

	private void writeToFile(SgyFile file, List<Position> positions) throws IOException {
		log.debug("Writing to file {}: positions -> {}, geo data -> {}",
				file.getFile() != null ? file.getFile().getName() : "Unknown",
				positions.size(),
				file.getGeoData().size());
		if (file instanceof CsvFile csvFile) {
			writeToCsvFile(csvFile, positions);
		} else if (file instanceof GprFile gprFile) {
			writeToSgyFile(gprFile, positions);
		}
	}

	private void writeToCsvFile(CsvFile csvFile, List<Position> positions) {
		List<GeoData> geoData = csvFile.getGeoData();
		List<GeoData> updatedGeoData = updateGeoData(positions, geoData);
		csvFile.setGeoData(updatedGeoData);
		csvFile.setUnsaved(true);
	}

	private void writeToSgyFile(GprFile gprFile, List<Position> positions) {
		List<Trace> traces = gprFile.getTraces();

		if (traces.size() != positions.size()) {
			throw new IllegalStateException("Number of traces and coordinates do not match.");
		}
		for (int i = 0; i < traces.size(); i++) {
			Trace trace = traces.get(i);
			Position position = positions.get(i);

			if (position.getLatitude() != null && position.getLongitude() != null) {
				trace.setLatLon(new LatLon(position.getLatitude(), position.getLongitude()));
			}
		}
		gprFile.setUnsaved(true);
	}

	private List<GeoData> updateGeoData(List<Position> positions, List<GeoData> geoData) {
		List<GeoData> resultGeodata = new ArrayList<>(positions.size());
		ColumnSchema columnSchema = GeoData.getSchema(geoData);
		for (Position position : positions) {
			GeoData gd = new GeoData(columnSchema);
			gd.setLatitude(position.getLatitude());
			gd.setLongitude(position.getLongitude());
			gd.setAltitude(position.getAltitude());
			gd.setDateTime(LocalDateTime.ofInstant(
					Instant.ofEpochMilli(position.getTimeMs()), ZoneOffset.UTC)
			);
			resultGeodata.add(gd);
		}
		return resultGeodata;
	}

	private List<Position> getPositions(List<SgyFile> positionFiles) throws IOException {
		List<Position> allPositions = new ArrayList<>();
		for (SgyFile positionFile : positionFiles) {
			List<Position> positions = getPositions(positionFile);
			allPositions.addAll(positions);
		}
		return allPositions;
	}

	private List<Position> getPositions(SgyFile sgyFile) {
		if (sgyFile instanceof GprFile gprFile) {
			return getPositionsFromGpr(gprFile);
		} else if (sgyFile instanceof CsvFile csvFile) {
			return getPositionsFromCsv(csvFile);
		} else {
			return List.of();
		}
	}

	private List<Position> getPositionsFromCsv(CsvFile csvFile) {
		List<GeoData> geoData = csvFile.getGeoData();
		return geoData.stream().map(this::createPosition).toList();
	}

	private List<Position> getPositionsFromGpr(GprFile gprFile) {
		if (gprFile == null) {
			return List.of();
		}
		List<GeoData> geoData = gprFile.getGeoData();
		return geoData.stream().map(this::createPosition).toList();
	}

	private Position createPosition(GeoData geoData) {
		LocalDateTime dateTime = geoData.getDateTime();
		if (dateTime == null) {
			return new Position(null, geoData.getLatitude(), geoData.getLongitude(), geoData.getAltitude());
		}
		return new Position(
				geoData.getDateTime().toInstant(ZoneOffset.UTC).toEpochMilli(),
				geoData.getLatitude(),
				geoData.getLongitude(),
				geoData.getAltitude()
		);
	}

	private List<Position> interpolatePositions(SgyFile dataFile, List<Position> positions,
												AtomicInteger processedRows, int totalRows,
												Consumer<Integer> progressCallback) throws IOException {
		List<Position> correctedTraces = new ArrayList<>();

		positions.sort(Comparator.comparing(Position::getTimeMs, Comparator.nullsLast(Long::compareTo)));

		if (positions.isEmpty()) {
			return correctedTraces;
		}

		List<Position> dataFilePositions = getPositions(dataFile);

		for (Position dataFilePosition : dataFilePositions) {
			Long targetTime = dataFilePosition.getTimeMs();
			if (targetTime == null) {
				continue;
			}

			int leftIndex = -1;
			for (int i = 0; i < positions.size(); i++) {
				Long timeMs = positions.get(i).getTimeMs();
				if (timeMs != null && timeMs <= targetTime) {
					leftIndex = i;
				}
			}
			if (leftIndex < 0) {
				continue;
			}

			int rightIndex = -1;
			for (int i = 0; i < positions.size(); i++) {
				Long timeMs = positions.get(i).getTimeMs();
				if (timeMs != null && timeMs > targetTime) {
					rightIndex = i;
					break;
				}
			}
			if (rightIndex < 0) {
				continue;
			}

			Position left = positions.get(leftIndex);
			Position right = positions.get(rightIndex);

			Long leftTime = left.getTimeMs();
			Long rightTime = right.getTimeMs();
			if (leftTime == null || rightTime == null) {
				continue;
			}

			if (Math.abs(rightTime - leftTime) > MAX_TIME_DIFFERENCE_MS) {
				continue;
			}

			Double leftLat = left.getLatitude();
			Double leftLon = left.getLongitude();
			Double rightLat = right.getLatitude();
			Double rightLon = right.getLongitude();

			if (leftLat == null || rightLat == null || leftLon == null || rightLon == null) {
				continue;
			}

			double newLat = LinearInterpolator.interpolate(targetTime, leftLat, rightLat, leftTime, rightTime);
			double newLon = LinearInterpolator.interpolate(targetTime, leftLon, rightLon, leftTime, rightTime);
			dataFilePosition.setLatitude(newLat);
			dataFilePosition.setLongitude(newLon);

			Double leftAlt = left.getAltitude();
			Double rightAlt = right.getAltitude();
			if (leftAlt != null && rightAlt != null) {
				double newAlt = LinearInterpolator.interpolate(targetTime, leftAlt, rightAlt, leftTime, rightTime);
				dataFilePosition.setAltitude(newAlt);
			}

			correctedTraces.add(dataFilePosition);
			int current = processedRows.incrementAndGet();
			int percent = (int) ((current * 100L) / totalRows);
			progressCallback.accept(percent);
		}

		return correctedTraces;
	}
}
