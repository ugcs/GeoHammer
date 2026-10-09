package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Semantic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HeadingCorrectionServiceTest {

    private final HeadingCorrectionService service = new HeadingCorrectionService();

    @Test
    void correct_removesHeadingBiasAtCrossovers() {
        List<GeoData> data = List.of(
                sample(1, 0, 0, 100), sample(1, 0, 2, 100),
                sample(2, -1, 1, 110), sample(2, 1, 1, 110)
        );

        HeadingCorrectionResult result = service.correct(data, "TMI", 8);

        assertEquals(List.of(100.0, 100.0, 100.0, 100.0), result.values());
        assertEquals(1, result.crossovers().size());
        assertEquals(0.0, result.rmsError());
    }

    @Test
    void correct_requiresCrossoverObservations() {
        List<GeoData> data = List.of(
                sample(1, 0, 0, 100), sample(1, 0, 1, 100),
                sample(2, 1, 0, 110), sample(2, 1, 1, 110)
        );

        assertThrows(IllegalArgumentException.class, () -> service.correct(data, "TMI", 8));
    }

    private static GeoData sample(int line, double latitude, double longitude, double field) {
        ColumnSchema schema = new ColumnSchema();
        schema.addColumn(new Column("Line").withSemantic(Semantic.LINE.getName()));
        schema.addColumn(new Column("Latitude").withSemantic(Semantic.LATITUDE.getName()));
        schema.addColumn(new Column("Longitude").withSemantic(Semantic.LONGITUDE.getName()));
        schema.addColumn(new Column("TMI"));
        GeoData value = new GeoData(schema);
        value.setLine(line);
        value.setLatLon(new LatLon(latitude, longitude));
        value.setValue("TMI", field);
        return value;
    }
}
