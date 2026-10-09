package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LineLevelingServiceTest {

    private final LineLevelingService service = new LineLevelingService();

    @Test
    void level_alignsLineMediansToSurveyMedian() {
        List<GeoData> data = List.of(sample(1, 10), sample(1, 12), sample(2, 20), sample(2, 22));

        assertEquals(List.of(15.0, 17.0, 15.0, 17.0), service.level(data, "TMI"));
    }

    @Test
    void level_requiresMultipleLines() {
        List<GeoData> data = List.of(sample(1, 10), sample(1, 12));

        assertThrows(IllegalArgumentException.class, () -> service.level(data, "TMI"));
    }

    private static GeoData sample(int line, double field) {
        ColumnSchema schema = new ColumnSchema();
        schema.addColumn(new Column("Line").withSemantic(Semantic.LINE.getName()));
        schema.addColumn(new Column("TMI"));
        GeoData value = new GeoData(schema);
        value.setLine(line);
        value.setValue("TMI", field);
        return value;
    }
}
