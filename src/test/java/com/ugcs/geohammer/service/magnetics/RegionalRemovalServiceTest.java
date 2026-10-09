package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Semantic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionalRemovalServiceTest {

    private final RegionalRemovalService service = new RegionalRemovalService();

    @Test
    void remove_subtractsLinearRegionalField() {
        List<GeoData> data = new ArrayList<>();
        for (int latitude = 0; latitude < 3; latitude++) {
            for (int longitude = 0; longitude < 3; longitude++) {
                data.add(sample(latitude, longitude, 100 + 2 * latitude + 3 * longitude));
            }
        }

        RegionalRemovalResult result = service.remove(data, "TMI", 1);

        for (Number value : result.values()) {
            assertEquals(0, value.doubleValue(), 1e-9);
        }
        assertEquals(9, result.sampleCount());
        assertEquals(0, result.rmsError(), 1e-9);
    }

    @Test
    void remove_subtractsQuadraticRegionalField() {
        List<GeoData> data = new ArrayList<>();
        for (int latitude = 0; latitude < 3; latitude++) {
            for (int longitude = 0; longitude < 3; longitude++) {
                double field = 100 + 2 * latitude + 3 * longitude + latitude * latitude
                        + 2 * latitude * longitude + 3 * longitude * longitude;
                data.add(sample(latitude, longitude, field));
            }
        }

        RegionalRemovalResult result = service.remove(data, "TMI", 2);

        for (Number value : result.values()) {
            assertEquals(0, value.doubleValue(), 1e-9);
        }
    }

    @Test
    void remove_rejectsUnderdeterminedPolynomial() {
        List<GeoData> data = List.of(sample(0, 0, 100), sample(0, 1, 101), sample(1, 0, 102));

        assertThrows(IllegalArgumentException.class, () -> service.remove(data, "TMI", 2));
    }

    private static GeoData sample(double latitude, double longitude, double field) {
        ColumnSchema schema = new ColumnSchema();
        schema.addColumn(new Column("Latitude").withSemantic(Semantic.LATITUDE.getName()));
        schema.addColumn(new Column("Longitude").withSemantic(Semantic.LONGITUDE.getName()));
        schema.addColumn(new Column("TMI"));
        GeoData value = new GeoData(schema);
        value.setLatLon(new LatLon(latitude, longitude));
        value.setValue("TMI", field);
        return value;
    }
}
