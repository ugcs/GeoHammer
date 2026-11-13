package com.ugcs.geohammer.service.survey;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.util.Nulls;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;

@Service
public class SurveyService {

    public OptionalDouble estimateLineSpacing(SgyFile file) {
        if (file == null) {
            return OptionalDouble.empty();
        }

        List<GeoData> values = Nulls.toEmpty(file.getGeoData());
        ColumnSchema schema = GeoData.getSchema(values);
        if (schema == null) {
            return OptionalDouble.empty();
        }
        int latitudeIndex = schema.getColumnIndex(schema.getHeaderBySemantic(Semantic.LATITUDE.getName()));
        int longitudeIndex = schema.getColumnIndex(schema.getHeaderBySemantic(Semantic.LONGITUDE.getName()));
        if (latitudeIndex == -1 || longitudeIndex == -1) {
            return OptionalDouble.empty();
        }

        // track of the file: positions in row order, in local meters
        double[] x = new double[values.size()];
        double[] y = new double[values.size()];
        int n = 0;

        // equirectangular projection around the first position
        double originLatitude = 0;
        double originLongitude = 0;
        double longitudeScale = 0;
        for (GeoData value : values) {
            Number latitude = value.getNumber(latitudeIndex);
            Number longitude = value.getNumber(longitudeIndex);
            if (latitude == null || longitude == null) {
                continue;
            }
            double latitudeValue = latitude.doubleValue();
            double longitudeValue = longitude.doubleValue();
            if (!Double.isFinite(latitudeValue) || !Double.isFinite(longitudeValue)) {
                continue;
            }
            if (n == 0) {
                originLatitude = latitudeValue;
                originLongitude = longitudeValue;
                longitudeScale = Math.cos(Math.toRadians(originLatitude)) * SphericalMercator.R;
            }
            x[n] = Math.toRadians(longitudeValue - originLongitude) * longitudeScale;
            y[n] = Math.toRadians(latitudeValue - originLatitude) * SphericalMercator.R;
            n++;
        }
        if (n < x.length) {
            x = Arrays.copyOf(x, n);
            y = Arrays.copyOf(y, n);
        }
        return LineSpacing.estimate(x, y);
    }
}
