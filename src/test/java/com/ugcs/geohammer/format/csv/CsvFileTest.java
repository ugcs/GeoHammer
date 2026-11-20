package com.ugcs.geohammer.format.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.csv.parser.BaseParsersTest;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.template.Template;

class CsvFileTest extends BaseParsersTest {

    private static final double EPS = 1e-9;

    private static final String TEMPLATE_PATH = YamlTestDataFolder + YamlCsvFolder + "MissingValuesTemplate.yaml";

    private static final String FILE_PATH = CSVTestDataFolder + "missing-values.csv";

    private static final String UNIQUE_HEADERS_TEMPLATE_PATH
            = YamlTestDataFolder + YamlCsvFolder + "UniqueHeadersTemplate.yaml";

    @TempDir
    Path directory;

    @Test
    void open_integerColumnWithGaps_keepsGapsEmpty() throws IOException {
        CsvFile file = openFile();

        assertNull(getValue(file, 1, "IntCol"));
    }

    @Test
    void open_wholeNumbersWithDecimalSeparator_fillsGaps() throws IOException {
        CsvFile file = openFile();

        assertEquals(15.0, getNumber(file, 1, "FloatCol"), EPS);
        assertEquals(50.0, getNumber(file, 5, "FloatCol"), EPS);
    }

    @Test
    void open_integerAndDecimalValuesInColumn_fillsGaps() throws IOException {
        CsvFile file = openFile();

        assertEquals(10.75, getNumber(file, 1, "MixedCol"), EPS);
        assertEquals(14.0, getNumber(file, 5, "MixedCol"), EPS);
    }

    @Test
    void open_decimalValueInAnotherLine_fillsGapsInAllLines() throws IOException {
        CsvFile file = openFile();

        assertEquals(15.0, getNumber(file, 1, "CrossCol"), EPS);
        assertEquals(4.0, getNumber(file, 5, "CrossCol"), EPS);
    }

    @Test
    void open_filledValues_opensWithoutUnsavedChanges() throws IOException {
        CsvFile file = openFile();

        assertFalse(file.isUnsaved());
    }

    @Test
    void save_integerColumnWithGaps_writesEmptyCells() throws IOException {
        CsvFile file = openFile();
        File saved = directory.resolve("saved.csv").toFile();

        file.save(saved);

        List<String> lines = Files.readAllLines(saved.toPath());
        int intColumn = Arrays.asList(lines.getFirst().split(",")).indexOf("IntCol");
        assertEquals("", lines.get(2).split(",", -1)[intColumn]);
        assertEquals("15", lines.get(2).split(",", -1)[intColumn + 1]);
    }

    @Test
    void save_repeatedAndEmptyHeaders_writesUniqueHeaders() throws IOException {
        Path source = directory.resolve("source.csv");
        Files.write(source, List.of(
                "Lat,Lon,Header,Header,,",
                "50,20,1,2,3,4",
                "51,21,5,6,7,8"));
        CsvFile file = openFile(UNIQUE_HEADERS_TEMPLATE_PATH, source.toFile());
        File saved = directory.resolve("saved.csv").toFile();

        file.save(saved);

        // line and mark columns are appended on save
        List<String> lines = Files.readAllLines(saved.toPath());
        assertTrue(lines.get(0).startsWith("Lat,Lon,Header,Header_1,column_4,column_5,"), lines.get(0));
        assertTrue(lines.get(1).startsWith("50,20,1,2,3,4,"), lines.get(1));
        assertTrue(lines.get(2).startsWith("51,21,5,6,7,8,"), lines.get(2));

        CsvFile reopened = openFile(UNIQUE_HEADERS_TEMPLATE_PATH, saved);
        assertEquals(List.of("Lat", "Lon", "Header", "Header_1", "column_4", "column_5"),
                reopened.getParser().getHeaders().subList(0, 6));
        assertTrue(reopened.getParser().getWarnings().isEmpty());
    }

    private CsvFile openFile() throws IOException {
        return openFile(TEMPLATE_PATH, new File(FILE_PATH));
    }

    private CsvFile openFile(String templatePath, File source) throws IOException {
        Template template = deserializer.load(Files.readString(Path.of(templatePath)));
        template.init();
        CsvFile file = new CsvFile(null);
        file.open(source, template);
        return file;
    }

    private static Object getValue(CsvFile file, int row, String header) {
        List<GeoData> values = file.getGeoData();
        ColumnSchema schema = GeoData.getSchema(values);
        return values.get(row).getValue(schema.getColumnIndex(header));
    }

    private static double getNumber(CsvFile file, int row, String header) {
        return ((Number)getValue(file, row, header)).doubleValue();
    }
}
