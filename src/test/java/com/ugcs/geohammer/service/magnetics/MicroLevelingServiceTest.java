package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MicroLevelingServiceTest {

    private final MicroLevelingService service = new MicroLevelingService();

    @Test
    void level_removesSlowResidualFromInteriorLine() {
        List<GeoData> data = new ArrayList<>();
        addLine(data, 1, 10, 20, 30, 40, 50);
        addLine(data, 2, 20, 30, 40, 50, 60);
        addLine(data, 3, 10, 20, 30, 40, 50);

        assertEquals(List.of(10.0, 20.0, 30.0, 40.0, 50.0,
                        10.0, 20.0, 30.0, 40.0, 50.0,
                        10.0, 20.0, 30.0, 40.0, 50.0),
                service.level(data, "TMI", 3));
    }

    @Test
    void level_preservesLocalizedAnomaly() {
        List<GeoData> data = new ArrayList<>();
        addLine(data, 1, 10, 10, 10, 10, 10);
        addLine(data, 2, 10, 10, 110, 10, 10);
        addLine(data, 3, 10, 10, 10, 10, 10);

        assertEquals(110.0, service.level(data, "TMI", 3).get(7));
    }

    @Test
    void level_requiresThreeLines() {
        List<GeoData> data = new ArrayList<>();
        addLine(data, 1, 10, 20);
        addLine(data, 2, 10, 20);

        assertThrows(IllegalArgumentException.class, () -> service.level(data, "TMI", 3));
    }

    private static void addLine(List<GeoData> data, int line, double... fields) {
        for (double field : fields) {
            data.add(sample(line, field));
        }
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
