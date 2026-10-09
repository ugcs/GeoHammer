package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossoverLevelingServiceTest {

    private final CrossoverLevelingService service = new CrossoverLevelingService();

    @Test
    void level_alignsSurveyLineWithTieLineAtCrossover() {
        List<GeoData> data = List.of(
                sample(1, -1, 0, 100), sample(1, 1, 0, 100),
                sample(2, 0, -1, 110), sample(2, 0, 1, 110));

        CrossoverLevelingResult result = service.level(data, "TMI", Set.of(2));

        assertEquals(1, result.crossovers().size());
        assertEquals(10, result.corrections().get(1));
        assertEquals(List.of(110.0, 110.0, 110.0, 110.0), result.values());
        assertEquals(10, result.rmsError());
    }

    private static GeoData sample(int line, double latitude, double longitude, double field) {
        ColumnSchema schema = new ColumnSchema();
        schema.addColumn(new Column("Line").withSemantic(Semantic.LINE.getName()));
        schema.addColumn(new Column("Latitude").withSemantic(Semantic.LATITUDE.getName()));
        schema.addColumn(new Column("Longitude").withSemantic(Semantic.LONGITUDE.getName()));
        schema.addColumn(new Column("TMI"));
        GeoData value = new GeoData(schema);
        value.setLine(line);
        value.setLatitude(latitude);
        value.setLongitude(longitude);
        value.setValue("TMI", field);
        return value;
    }
}
