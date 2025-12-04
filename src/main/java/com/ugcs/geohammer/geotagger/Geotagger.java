package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

import com.ugcs.geohammer.Loader;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.geotagger.domain.Position;
import com.ugcs.geohammer.geotagger.domain.Segment;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileUpdatedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class Geotagger {

	private final Model model;
	private final Loader loader;


	public Geotagger(Model model, Loader loader) {
		this.model = model;
		this.loader = loader;
	}

	public List<Position> getPositions(SgyFile file) {
		return file.getGeoData().stream()
				.map(Position::of).toList();
	}

	public List<Position> getPositions(List<SgyFile> files) {
		List<Position> allPositions = new ArrayList<>();
		for (SgyFile file : files) {
			List<Position> positions = getPositions(file);
			allPositions.addAll(positions);
		}
		allPositions.sort(Comparator.comparingLong(Position::time));
		return allPositions;
	}

	public void interpolateAndUpdatePositions(
			List<SgyFile> dataFiles,
			List<SgyFile> positionFiles,
			BiConsumer<Integer, Integer> onProgress) throws IOException {
		List<Position> positions = getPositions(positionFiles);

		int totalRows = dataFiles.stream()
				.mapToInt(sgyFile -> sgyFile.getGeoData().size())
				.sum();
		Progress progress = new Progress(totalRows, onProgress);
		for (SgyFile dataFile : dataFiles) {
			File tempFile = createTempFileIfNeeded(dataFile);
			interpolateAndUpdatePositions(dataFile, positions, progress);
			save(dataFile, tempFile);
			reloadFromTempIfNeeded(dataFile, tempFile);
		}
	}

	@Nullable
	private File createTempFileIfNeeded(SgyFile sgyFile) throws IOException {
		File tempFile = null;
		if (isOpenedInGeohammer(sgyFile)) {
			tempFile = sgyFile.copyToTempFile();
		}
		return tempFile;
	}

	private boolean isOpenedInGeohammer(SgyFile file) {
		return model.getFileManager().getFiles().contains(file);
	}

	private void interpolateAndUpdatePositions(
			SgyFile dataFile,
			List<Position> positions,
			Progress progress
	) {
		// todo for SGY file use traces + sync meta
		if (dataFile instanceof CsvFile csvFile) {
			for (GeoData value : csvFile.getGeoData()) {
				interpolateAndUpdate(value, positions);
				progress.increment();
			}
		} else if (dataFile instanceof TraceFile traceFile) {
			for (int i = 0; i < traceFile.getTraces().size(); i++) {
				Trace trace = traceFile.getTraces().get(i);
				GeoData geodata = traceFile.getGeoData().get(trace.getIndex());
				interpolateAndUpdate(trace, geodata, positions);
				traceFile.syncMeta();
				progress.increment();
			}
		}
	}

	private void interpolateAndUpdate(GeoData value, List<Position> positions) {
		LocalDateTime dateTime = value.getDateTime();
		if (dateTime == null) {
			return;
		}
		long time = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
		Segment segment = findSegment(positions, time);
		if (segment == null) {
			return;
		}
		Position interpolated = segment.interpolatePosition(time);
		// update value
		value.setLatitude(interpolated.latitude());
		value.setLongitude(interpolated.longitude());
		value.setAltitude(interpolated.altitude());
	}

	private void interpolateAndUpdate(Trace trace, GeoData geoData, List<Position> positions) {
		LocalDateTime dateTime = geoData.getDateTime();
		if (dateTime == null) {
			return;
		}
		long time = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
		Segment segment = findSegment(positions, time);
		if (segment == null) {
			return;
		}
		Position interpolated = segment.interpolatePosition(time);
		// update value
		trace.setLatLon(
				new LatLon(
						interpolated.latitude(),
						interpolated.longitude()
				)
		);
	}

	@Nullable
	private Segment findSegment(List<Position> positions, long time) {
		if (positions.isEmpty()) {
			return null;
		}
		if (time < positions.getFirst().time() || time > positions.getLast().time()) {
			return null;
		}
		int index = Collections.binarySearch(positions,
				new Position(time, 0.0, 0.0, 0.0),
				Comparator.comparingLong(Position::time));
		if (index < 0) {
			index = -index - 1;
		}
		return new Segment(
				positions.get(Math.max(index - 1, 0)),
				positions.get(Math.min(index, positions.size() - 1))
		);
	}

	private void save(SgyFile sgyFile, @Nullable File tempFile) throws IOException {
		File file = tempFile != null ? tempFile : sgyFile.getFile();
		if (file != null) {
			sgyFile.save(file);
		}
	}

	private void reloadFromTempIfNeeded(SgyFile sgyFile, @Nullable File tempFile) throws IOException {
		if (isOpenedInGeohammer(sgyFile) && tempFile != null) {
//			loader.loadFrom(sgyFile, tempFile);

			sgyFile.setUnsaved(true);
			sgyFile.tracesChanged();

			Chart chart = model.getChart(sgyFile);
			if (chart != null) {
				Platform.runLater(chart::reload);
			}

			model.updateAuxElements();

			model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
			model.publishEvent(new FileUpdatedEvent(this, sgyFile));
		}
	}
}
