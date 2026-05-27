package com.ugcs.geohammer.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;

class GeoDataTest {

    private static final double EPS = 1e-9;

    private ColumnSchema schema;

    @BeforeEach
    void setUp() {
        schema = new ColumnSchema();
        schema.addColumn(new Column("lat").withSemantic(Semantic.LATITUDE.getName()));
        schema.addColumn(new Column("lon").withSemantic(Semantic.LONGITUDE.getName()));
    }

    private GeoData row(Double lat, Double lon) {
        GeoData value = new GeoData(schema);
        if (lat != null) {
            value.setLatitude(lat);
        }
        if (lon != null) {
            value.setLongitude(lon);
        }
        return value;
    }

    @Test
    void fillMissingLatLonFillsMiddleGap() {
        List<GeoData> values = List.of(
                row(0.0, 10.0),
                row(null, null),
                row(null, null),
                row(3.0, 13.0));

        GeoData.fillMissingLatLon(values);

        assertEquals(1.0, values.get(1).getLatitude(), EPS);
        assertEquals(11.0, values.get(1).getLongitude(), EPS);
        assertEquals(2.0, values.get(2).getLatitude(), EPS);
        assertEquals(12.0, values.get(2).getLongitude(), EPS);
    }

    @Test
    void fillMissingLatLonReplicatesLeadingAndTrailingGaps() {
        List<GeoData> values = List.of(
                row(null, null),
                row(5.0, 50.0),
                row(6.0, 60.0),
                row(null, null));

        GeoData.fillMissingLatLon(values);

        assertEquals(5.0, values.get(0).getLatitude(), EPS);
        assertEquals(50.0, values.get(0).getLongitude(), EPS);
        assertEquals(6.0, values.get(3).getLatitude(), EPS);
        assertEquals(60.0, values.get(3).getLongitude(), EPS);
    }

    @Test
    void fillMissingLatLonLeavesAllMissingRowsUnchanged() {
        List<GeoData> values = List.of(
                row(null, null),
                row(null, null));

        GeoData.fillMissingLatLon(values);

        assertNull(values.get(0).getLatitude());
        assertNull(values.get(0).getLongitude());
        assertNull(values.get(1).getLatitude());
        assertNull(values.get(1).getLongitude());
    }

    @Test
    void fillMissingLatLonLeavesFullyValidRowsUnchanged() {
        List<GeoData> values = List.of(
                row(1.0, 11.0),
                row(2.0, 12.0));

        GeoData.fillMissingLatLon(values);

        assertEquals(1.0, values.get(0).getLatitude(), EPS);
        assertEquals(11.0, values.get(0).getLongitude(), EPS);
        assertEquals(2.0, values.get(1).getLatitude(), EPS);
        assertEquals(12.0, values.get(1).getLongitude(), EPS);
    }

    @Test
    void fillMissingLatLonTreatsHalfMissingRowAsGap() {
        List<GeoData> values = List.of(
                row(1.0, 11.0),
                row(null, 12.0),
                row(3.0, 13.0));

        GeoData.fillMissingLatLon(values);

        assertEquals(2.0, values.get(1).getLatitude(), EPS);
        assertEquals(12.0, values.get(1).getLongitude(), EPS);
    }

    @Test
    void fillMissingLatLonHandlesEmptyAndNullInput() {
        GeoData.fillMissingLatLon(null);
        GeoData.fillMissingLatLon(new ArrayList<>());
    }
}
