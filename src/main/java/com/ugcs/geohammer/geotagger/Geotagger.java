package com.ugcs.geohammer.geotagger;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.domain.Position;
import com.ugcs.geohammer.geotagger.domain.Segment;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileUpdatedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Geotagger {

	private static final Logger log = LoggerFactory.getLogger(Geotagger.class);
	private final Model model;

	public Geotagger(Model model) {
		this.model = model;
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
            BiConsumer<Integer, Integer> onProgress) {
        List<Position> positions = getPositions(positionFiles);

        int totalRows = dataFiles.stream()
                .mapToInt(sgyFile -> sgyFile.getGeoData().size())
                .sum();
        Progress progress = new Progress(totalRows, onProgress);
        for (SgyFile dataFile : dataFiles) {
            interpolateAndUpdatePositions(dataFile, positions, progress);
            save(dataFile);
        }
	}

	private void interpolateAndUpdatePositions(
			SgyFile dataFile,
			List<Position> positions,
			Progress progress
	) {
		for (GeoData value : dataFile.getGeoData()) {
			interpolateAndUpdate(value, positions);
			progress.increment();
		}
	}

	private void interpolateAndUpdate(GeoData value, List<Position> positions) {
		long time = value.getDateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
		Segment segment = findSegment(positions, time);
		Position interpolated = segment.interpolatePosition(time);
		// update value
		value.setLatitude(interpolated.latitude());
		value.setLongitude(interpolated.longitude());
		value.setAltitude(interpolated.altitude());
	}

	private Segment findSegment(List<Position> positions, long time) {
		int index = Collections.binarySearch(positions,
				new Position(time, 0.0, 0.0, 0.0),
				Comparator.comparingLong(Position::time));
		if (index < 0) {
			index = -index - 1;
		}
		return new Segment(
				positions.get(Math.min(index, positions.size() - 1)),
				positions.get(Math.min(index + 1, positions.size() - 1))
		);
	}

	private void save(SgyFile file) {
		file.setUnsaved(true);

		file.tracesChanged();

		Chart chart = model.getFileChart(file);
		if (chart != null) {
			Platform.runLater(chart::reload);
		}

		model.updateAuxElements();

		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
		model.publishEvent(new FileUpdatedEvent(this, file));
	}
}
