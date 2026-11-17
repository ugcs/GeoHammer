package com.ugcs.geohammer.service.gridding;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.csv.parser.BaseParsersTest;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.template.Template;

class GriddingServiceTest extends BaseParsersTest {

    private static final String TEMPLATE_PATH = YamlTestDataFolder + YamlCsvFolder + "MissingValuesTemplate.yaml";

    private static final String FILE_PATH = CSVTestDataFolder + "missing-values.csv";

    private static final String SERIES = "IntCol";

    private static final GriddingParams PARAMS = new GriddingParams(100, 200);

    // survey of lines running north, with an area without data enclosed in the middle
    private static final String SURVEY_SERIES = "value";

    private static final GriddingParams SURVEY_PARAMS = new GriddingParams(0.8, 4);

    private static final double SURVEY_LATITUDE = 56;

    private static final double SURVEY_LONGITUDE = 24;

    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;

    private static final double METERS_PER_LONGITUDE_DEGREE
            = METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(SURVEY_LATITUDE));

    private static final double LINE_SPACING = 4;

    private static final int NUM_LINES = 15;

    private static final double LINE_LENGTH = 60;

    private static final double POINT_STEP = 0.5;

    private static final int FIRST_HOLE_LINE = 5;

    private static final int LAST_HOLE_LINE = 9;

    private static final double HOLE_START = 20;

    private static final double HOLE_END = 40;

    // the grid extends beyond the survey by one cell of margin and up to one cell of rounding
    private static final double MIN_GRID_PADDING = SURVEY_PARAMS.cellSize();

    private static final double MAX_GRID_PADDING = 2.5 * SURVEY_PARAMS.cellSize();

    // the size of the grid around the data slightly changes the interpolation
    private static final float VALUE_TOLERANCE = 0.05f;

    private final GriddingService griddingService = new GriddingService();

    @Test
    void runGridding_severalFiles_returnsSharedResult() throws IOException {
        GriddingResult result = griddingService.runGridding(List.of(openFile(), openFile()), SERIES, PARAMS);

        assertNotNull(result);
        assertTrue(result.shared());
    }

    @Test
    void runGridding_singleFile_returnsIndividualResult() throws IOException {
        GriddingResult result = griddingService.runGridding(List.of(openFile()), SERIES, PARAMS);

        assertNotNull(result);
        assertFalse(result.shared());
    }

    @Test
    void runGridding_survey_extendsGridByMargin() {
        GriddingResult result = griddingService.runGridding(List.of(createSurvey()), SURVEY_SERIES, SURVEY_PARAMS);

        assertNotNull(result);
        // on a line and between lines
        assertTrue(Float.isFinite(getValue(result, 8, 10)));
        assertTrue(Float.isFinite(getValue(result, 10, 10)));
        // the grid ends within the margin and a cell of rounding around the survey,
        // not the blanking distance
        double surveyWidth = (NUM_LINES - 1) * LINE_SPACING;
        double west = (SURVEY_LONGITUDE - result.minLatLon().getLonDgr()) * METERS_PER_LONGITUDE_DEGREE;
        double east = (result.maxLatLon().getLonDgr() - toLongitude(surveyWidth)) * METERS_PER_LONGITUDE_DEGREE;
        double south = (SURVEY_LATITUDE - result.minLatLon().getLatDgr()) * METERS_PER_LATITUDE_DEGREE;
        double north = (result.maxLatLon().getLatDgr() - toLatitude(LINE_LENGTH)) * METERS_PER_LATITUDE_DEGREE;
        for (double padding : new double[] {west, east, south, north}) {
            assertTrue(padding >= MIN_GRID_PADDING && padding <= MAX_GRID_PADDING, "grid padding " + padding);
        }
    }

    @Test
    void runGridding_enclosedGap_clipsGridToOutline() {
        GriddingResult result = griddingService.runGridding(List.of(createSurvey()), SURVEY_SERIES, SURVEY_PARAMS);

        assertNotNull(result);
        // on the lines around the gap
        assertTrue(Float.isFinite(getValue(result, 16, 30)));
        assertTrue(Float.isFinite(getValue(result, 40, 30)));
        // within the blanking distance from the lines around the gap
        assertTrue(Float.isNaN(getValue(result, 18.5, 30)));
        assertTrue(Float.isNaN(getValue(result, 37.5, 30)));
        assertTrue(Float.isNaN(getValue(result, 28, 22.5)));
        // gap center
        assertTrue(Float.isNaN(getValue(result, 28, 30)));
    }

    @Test
    void runGridding_insideSurvey_interpolatesValues() {
        GriddingResult result = griddingService.runGridding(List.of(createSurvey()), SURVEY_SERIES, SURVEY_PARAMS);

        assertNotNull(result);
        // values of the same grid built with a padding of the blanking distance around the data
        assertAll(
                () -> assertEquals(112.53217f, getValue(result, 10, 10), VALUE_TOLERANCE),
                () -> assertEquals(94.4659f, getValue(result, 30, 10), VALUE_TOLERANCE),
                () -> assertEquals(101.29328f, getValue(result, 46, 50), VALUE_TOLERANCE));
    }

    private CsvFile createSurvey() {
        ColumnSchema schema = new ColumnSchema();
        schema.addColumn(new Column("lat").withSemantic(Semantic.LATITUDE.getName()));
        schema.addColumn(new Column("lon").withSemantic(Semantic.LONGITUDE.getName()));
        schema.addColumn(new Column(SURVEY_SERIES));

        List<GeoData> values = new ArrayList<>();
        for (int line = 0; line < NUM_LINES; line++) {
            double east = line * LINE_SPACING;
            boolean hasHole = line >= FIRST_HOLE_LINE && line <= LAST_HOLE_LINE;
            for (double north = 0; north <= LINE_LENGTH; north += POINT_STEP) {
                if (hasHole && north > HOLE_START && north < HOLE_END) {
                    continue;
                }
                GeoData value = new GeoData(schema);
                value.setLatitude(toLatitude(north));
                value.setLongitude(toLongitude(east));
                value.setValue(SURVEY_SERIES, 100 + 10 * Math.sin(east / 7) + 5 * Math.cos(north / 11));
                values.add(value);
            }
        }

        CsvFile file = new CsvFile(null);
        file.setGeoData(values);
        return file;
    }

    // grid value at the offset in meters from the start of the first survey line
    private float getValue(GriddingResult result, double east, double north) {
        float[][] grid = result.grid();
        double minLatitude = result.minLatLon().getLatDgr();
        double maxLatitude = result.maxLatLon().getLatDgr();
        double minLongitude = result.minLatLon().getLonDgr();
        double maxLongitude = result.maxLatLon().getLonDgr();
        int x = (int) (grid.length * (toLongitude(east) - minLongitude) / (maxLongitude - minLongitude));
        int y = (int) (grid[0].length * (toLatitude(north) - minLatitude) / (maxLatitude - minLatitude));
        return grid[x][y];
    }

    private static double toLatitude(double north) {
        return SURVEY_LATITUDE + north / METERS_PER_LATITUDE_DEGREE;
    }

    private static double toLongitude(double east) {
        return SURVEY_LONGITUDE + east / METERS_PER_LONGITUDE_DEGREE;
    }

    private CsvFile openFile() throws IOException {
        Template template = deserializer.load(Files.readString(Path.of(TEMPLATE_PATH)));
        template.init();
        CsvFile file = new CsvFile(null);
        file.open(new File(FILE_PATH), template);
        return file;
    }
}
