package com.ugcs.geohammer.geotagger;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.geotagger.domain.Position;
import com.ugcs.geohammer.geotagger.domain.SplineStencil;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.util.Nulls;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
		if (file == null) {
			return List.of();
		}
		return Nulls.toEmpty(file.getGeoData()).stream()
				.map(Position::of)
				.filter(Objects::nonNull)
				.toList();
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
		List<GeoData> values = csvFile.getGeoData();
		boolean hasAltitude = !values.isEmpty()
				&& values.getFirst().getSchema().getHeaderBySemantic(Semantic.ALTITUDE.getName()) != null;

        for (GeoData value : values) {
            Position interpolated = interpolate(positions, value.getTimestamp());
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
			Instant time = trace.getDateTime();

			Position interpolated = interpolate(positions, time != null ? time.toEpochMilli() : null);
            if (interpolated != null) {
                trace.setLatLon(interpolated.getLatLon());
            }
            progress.increment();
        }
        traceFile.syncMeta();
    }

    private @Nullable Position interpolate(List<Position> positions, Long time) {
        if (time == null) {
            return null;
        }
		SplineStencil stencil = findStencil(positions, time);
        if (stencil == null) {
            return null;
        }
        return stencil.interpolate(time);
    }

	@Nullable
	private SplineStencil findStencil(List<Position> positions, long time) {
		if (positions.isEmpty()) {
			return null;
		}
		if (time < positions.getFirst().time() || time > positions.getLast().time()) {
			return null;
		}
		// first index >= time
		int i = Collections.binarySearch(positions,
				new Position(time, 0.0, 0.0, 0.0),
				Comparator.comparingLong(Position::time));
		if (i < 0) {
			i = -i - 1;
		}

		Position p2 = positions.get(Math.max(i - 2, 0));
		Position p1 = positions.get(Math.max(i - 1, 0));
		Position n1 = positions.get(Math.min(i, positions.size() - 1));
		Position n2 = positions.get(Math.min(i + 1, positions.size() - 1));

		return new SplineStencil(p2, p1, n1, n2);
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
