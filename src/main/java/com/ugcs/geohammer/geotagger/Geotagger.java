package com.ugcs.geohammer.geotagger;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.geotagger.domain.Position;
import com.ugcs.geohammer.geotagger.domain.Segment;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Model;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class Geotagger {

	private final Model model;

	public Geotagger(Model model) {
		this.model = model;
	}

    private boolean isOpenedInGeohammer(SgyFile file) {
        return model.getFileManager().getFiles().contains(file);
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

		int totalValues = dataFiles.stream()
				.mapToInt(SgyFile::numTraces)
				.sum();
		Progress progress = new Progress(totalValues, onProgress);
		for (SgyFile dataFile : dataFiles) {
			if (dataFile instanceof CsvFile csvFile) {
                interpolateAndUpdatePositions(csvFile, positions, progress);
            } else if (dataFile instanceof TraceFile traceFile) {
                interpolateAndUpdatePositions(traceFile, positions, progress);
            }
            if (isOpenedInGeohammer(dataFile)) {
                reload(dataFile);
            } else {
                save(dataFile);
            }
		}
	}

    private void interpolateAndUpdatePositions(CsvFile csvFile, List<Position> positions, Progress progress) {
		List<GeoData> geoData = csvFile.getGeoData();
		boolean hasAltitude = !geoData.isEmpty() && geoData.getFirst().hasAltitudeSemantic();

        for (GeoData value : geoData) {
            Position interpolated = interpolate(value, positions);
            if (interpolated != null) {
                value.setLatitude(interpolated.latitude());
                value.setLongitude(interpolated.longitude());
				if (hasAltitude) {
					value.setAltitude(interpolated.altitude());
				}
            }
            progress.increment();
        }
    }

    private void interpolateAndUpdatePositions(TraceFile traceFile, List<Position> positions, Progress progress) {
        for (int i = 0; i < traceFile.getTraces().size(); i++) {
            Trace trace = traceFile.getTraces().get(i);
            GeoData value = traceFile.getGeoData().get(i);

            Position interpolated = interpolate(value, positions);
            if (interpolated != null) {
                trace.setLatLon(
                        new LatLon(
                                interpolated.latitude(),
                                interpolated.longitude()
                        )
                );
            }
            progress.increment();
        }
        traceFile.syncMeta();
    }

    private @Nullable Position interpolate(GeoData value, List<Position> positions) {
        LocalDateTime dateTime = value.getDateTime();
        if (dateTime == null) {
            return null;
        }
        long time = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        Segment segment = findSegment(positions, time);
        if (segment == null) {
            return null;
        }
        return segment.interpolatePosition(time);
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

	private void save(SgyFile sgyFile) throws IOException {
		File file = sgyFile.getFile();
		if (file != null) {
			sgyFile.save(file);
		}
	}

	private void reload(SgyFile sgyFile) {
        sgyFile.setUnsaved(true);
        Platform.runLater(() -> model.reload(sgyFile));
	}
}
