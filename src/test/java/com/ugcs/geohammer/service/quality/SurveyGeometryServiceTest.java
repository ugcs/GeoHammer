package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyGeometryServiceTest {

    @Test
    void analyze_summarizesLineGeometryAndRecommendations() {
        SurveyGeometryReport report = new SurveyGeometryService().analyze(List.of(
                point(1, 44.0, -73.0), point(1, 44.01, -73.0), point(1, 44.02, -73.0),
                point(2, 44.0, -72.997), point(2, 44.01, -72.997), point(2, 44.02, -72.997),
                point(3, 44.0, -73.002), point(3, 44.0, -72.99), point(3, 44.0, -72.978),
                point(4, 44.0225, -73.002), point(4, 44.0225, -72.99), point(4, 44.0225, -72.978)));

        assertEquals(4, report.lineCount());
        assertEquals(2, report.primaryLineCount());
        assertEquals(2, report.tieLineCount());
        assertEquals(1, report.primaryLineSpacing().count());
        assertEquals(8, report.heading().count());
        assertEquals(2, report.tieLinePeakCount());
        assertEquals(report.primaryLineSpacing().median() / 4.0, report.recommendedCellSize(), 1e-9);
        assertEquals(report.primaryLineSpacing().median() * 2.0, report.recommendedBlankingDistance(), 1e-9);
        assertTrue(report.tieLinePeakSpacing() > report.primaryLineSpacing().median());
    }

    @Test
    void analyze_findsTieLinePeaksDespiteAnIrregularLine() {
        List<GeoData> data = new ArrayList<>();
        for (int line = 0; line < 6; line++) {
            double longitude = -73.0 + line * 0.003;
            addLine(data, line + 1, 44.0, longitude, 44.01, longitude);
        }
        addLine(data, 7, 44.0, -73.002, 44.0, -72.978);
        addLine(data, 8, 44.0225, -73.002, 44.0225, -72.978);
        addLine(data, 9, 44.045, -73.002, 44.045, -72.978);
        addLine(data, 10, 44.046, -73.002, 44.046, -72.978);
        addLine(data, 11, 44.0675, -73.002, 44.0675, -72.978);

        SurveyGeometryReport report = new SurveyGeometryService().analyze(data);

        assertEquals(2_500.0, report.tieLinePeakSpacing(), 250.0);
        assertEquals(4, report.tieLinePeakCount());
    }

    private static void addLine(List<GeoData> data, int line, double latitudeStart, double longitudeStart,
                                double latitudeEnd, double longitudeEnd) {
        data.add(point(line, latitudeStart, longitudeStart));
        data.add(point(line, latitudeEnd, longitudeEnd));
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
