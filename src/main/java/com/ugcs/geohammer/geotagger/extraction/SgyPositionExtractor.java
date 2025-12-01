package com.ugcs.geohammer.geotagger.extraction;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.geotagger.domain.Position;

public class SgyPositionExtractor implements PositionExtractor{
	@Override
	public List<Position> extractFrom(SgyFile file) {
		if (file instanceof TraceFile gprFile) {
			return getPositionsFromSgyFile(gprFile);
		} else if (file instanceof CsvFile csvFile) {
			return getPositionsFromSgyFile(csvFile);
		} else {
			return List.of();
		}
	}

	@Override
	public List<Position> extractFrom(List<SgyFile> files) {
		List<Position> allPositions = new ArrayList<>();
		for (SgyFile positionFile : files) {
			List<Position> positions = extractFrom(positionFile);
			allPositions.addAll(positions);
		}
		return allPositions;
	}

	private List<Position> getPositionsFromSgyFile(SgyFile sgyFile) {
		List<GeoData> geoData = sgyFile.getGeoData();
		return geoData.stream().map(this::createPosition).toList();
	}

	private Position createPosition(GeoData geoData) {
		return new Position(
				geoData.getDateTime().toInstant(ZoneOffset.UTC).toEpochMilli(),
				geoData.getLatitude(),
				geoData.getLongitude(),
				geoData.getAltitude()
		);
	}

}
