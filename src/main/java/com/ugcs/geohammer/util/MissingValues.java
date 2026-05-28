package com.ugcs.geohammer.util;

import java.util.List;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.math.LinearInterpolator;

public final class MissingValues {

	private MissingValues() {
	}

    public static void fillLatLon(List<GeoData> values) {
        if (Nulls.isNullOrEmpty(values)) {
            return;
        }

		List<Double> latitudes = GeoData.getColumnValues(values, GeoData::getLatitude, GeoData::setLatitude);
		List<Double> longitudes = GeoData.getColumnValues(values, GeoData::getLongitude, GeoData::setLongitude);

        LinearInterpolator.interpolateNans(latitudes);
        LinearInterpolator.interpolateNans(longitudes);
    }
}
