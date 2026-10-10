package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyGeometryServiceTest {

    @Test
    void analyze_summarizesLineGeometryAndRecommendations() {
        SurveyGeometryReport report = new SurveyGeometryService().analyze(List.of(
                point(1, 44.0, -73.0), point(1, 44.0001, -73.0), point(1, 44.0002, -73.0),
                point(2, 44.0, -72.999), point(2, 44.0001, -72.999), point(2, 44.0002, -72.999)));

        assertEquals(2, report.lineCount());
        assertEquals(4, report.sampleSpacing().count());
        assertEquals(2, report.lineSpacing().count());
        assertEquals(4, report.heading().count());
        assertTrue(report.recommendedCellSize() > 0.0);
        assertTrue(report.recommendedBlankingDistance() >= report.recommendedCellSize());
    }

    private static GeoData point(int line, double latitude, double longitude) {
        ColumnSchema schema = new ColumnSchema();
        schema.addColumn(new Column("LINE").withSemantic(Semantic.LINE.getName()));
        schema.addColumn(new Column("LAT").withSemantic(Semantic.LATITUDE.getName()));
        schema.addColumn(new Column("LON").withSemantic(Semantic.LONGITUDE.getName()));
        GeoData point = new GeoData(schema);
        point.setLine(line);
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        return point;
    }
}
