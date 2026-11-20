package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.GeoData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.model.template.Template;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

class CsvParserTest extends BaseParsersTest {

    private static final String UNIQUE_HEADERS_TEMPLATE_PATH
            = YamlTestDataFolder + YamlCsvFolder + "UniqueHeadersTemplate.yaml";

    @TempDir
    Path directory;

    private CsvParser csvParser;
    private String oldFile;
    private String newFile;
    private Iterable<GeoData> coordinates;

    @BeforeEach
    void setUp() {
        csvParser = null;
        oldFile = "path/to/oldFile";
        newFile = "path/to/newFile";
        coordinates = new ArrayList<>();
        // TODO: Add some GeoCoordinates to the coordinates list
    }

    private Template loadTemplate(String file) {
        Template template = deserializer.load(file);
        template.init();
        return template;
    }

    @Test
    void validCsv() throws IOException {

        String path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);

        try {
            parser.parse(Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position.csv").toFile());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void columnsMissmatchingCsv() throws IOException {

        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);

        assertThrows(ParseException.class, () -> {
            parser.parse(Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position-missed-headers.csv").toFile());
        });
    }

    @Test
    void missedDateCsv() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);

        assertThrows(ParseException.class, () -> {
            parser.parse(Paths.get(CSVTestDataFolder + "Missed-date-position.csv").toFile());
        });
    }

    @Test
    void validCsvWithoutHeaders() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplateWithoutHeaders.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);

        try {
            parser.parse(Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position-no-headers.csv").toFile());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void invalidCSV() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);

        // invalid csv also parcing if possible
        try {
            parser.parse(
                Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position-invalid.csv").toFile());
        } catch (Exception e) {
            fail(e.getMessage());
        }        
    }

    @Test
    void magdroneCSV() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlMagdroneFolder + "MagDroneValidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        Parser parser = new CsvParser(template);

        try {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagdroneFolder + "Magdrone.csv").toFile());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void magdroneCSVWithInvalidTemplate() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlMagdroneFolder + "MagDroneInvalidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        var parser = new CsvParser(template);

        Assertions.assertThrows(Exception.class, () -> {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagdroneFolder + "Magdrone.csv").toFile());
        });
    }

    @Test
    void magArrow() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlMagarrowFolder + "MagArrowValidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);

        try {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagarrowFolder + "MagArrow.csv").toFile());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void parse_repeatedHeader_keepsColumnValues() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        List<GeoData> values = parseLines(parser,
                "Time,Value,Value,Lat,Lon",
                "10:00:00,1,2,50,20");

        assertEquals(List.of("Time", "Value", "Value_1", "Lat", "Lon"), parser.getHeaders());
        GeoData value = values.getFirst();
        assertEquals(1, value.getNumber("Value").intValue());
        assertEquals(2, value.getNumber("Value_1").intValue());
        assertEquals(50, value.getNumber("Lat").intValue());
        assertEquals(20, value.getNumber("Lon").intValue());
    }

    @Test
    void parse_headerRepeatedSeveralTimes_incrementsSuffix() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        List<GeoData> values = parseLines(parser,
                "Lat,Lon,Mag,Mag,Mag",
                "50,20,1,2,3");

        assertEquals(List.of("Lat", "Lon", "Mag", "Mag_1", "Mag_2"), parser.getHeaders());
        GeoData value = values.getFirst();
        assertEquals(1, value.getNumber("Mag").intValue());
        assertEquals(2, value.getNumber("Mag_1").intValue());
        assertEquals(3, value.getNumber("Mag_2").intValue());
    }

    @Test
    void parse_emptyHeaders_namesColumnsByIndex() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        List<GeoData> values = parseLines(parser,
                "Lat,Lon,,,Mag",
                "50,20,1,2,3");

        assertEquals(List.of("Lat", "Lon", "column_2", "column_3", "Mag"), parser.getHeaders());
        GeoData value = values.getFirst();
        assertEquals(1, value.getNumber("column_2").intValue());
        assertEquals(2, value.getNumber("column_3").intValue());
        assertEquals(3, value.getNumber("Mag").intValue());
    }

    @Test
    void parse_repeatedAndEmptyHeaders_makesAllUnique() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        parseLines(parser,
                "Lat,Lon,Header,Header,,",
                "50,20,1,2,3,4");

        assertEquals(List.of("Lat", "Lon", "Header", "Header_1", "column_4", "column_5"), parser.getHeaders());
    }

    @Test
    void parse_suffixedHeaderInFile_keepsFileHeader() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        List<GeoData> values = parseLines(parser,
                "Lat,Lon,Header,Header,Header_1",
                "50,20,1,2,3");

        assertEquals(List.of("Lat", "Lon", "Header", "Header_2", "Header_1"), parser.getHeaders());
        GeoData value = values.getFirst();
        assertEquals(2, value.getNumber("Header_2").intValue());
        assertEquals(3, value.getNumber("Header_1").intValue());
    }

    @Test
    void parse_indexHeaderInFile_keepsFileHeader() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        List<GeoData> values = parseLines(parser,
                "column_1,,Lat,Lon",
                "1,2,50,20");

        assertEquals(List.of("column_1", "column_1_1", "Lat", "Lon"), parser.getHeaders());
        GeoData value = values.getFirst();
        assertEquals(1, value.getNumber("column_1").intValue());
        assertEquals(2, value.getNumber("column_1_1").intValue());
    }

    @Test
    void parse_uniqueHeaders_keepsHeaders() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        parseLines(parser,
                "Time,Value,value,Lat,Lon",
                "10:00:00,1,2,50,20");

        assertEquals(List.of("Time", "Value", "value", "Lat", "Lon"), parser.getHeaders());
    }

    @Test
    void parse_uniqueHeaders_hasNoWarnings() throws IOException {
        CsvParser parser = createUniqueHeadersParser();

        parseLines(parser,
                "Lat,Lon,Mag",
                "50,20,1");

        assertTrue(parser.getWarnings().isEmpty());
    }

    private CsvParser createUniqueHeadersParser() throws IOException {
        Template template = loadTemplate(Files.readString(Path.of(UNIQUE_HEADERS_TEMPLATE_PATH)));
        return new CsvParser(template);
    }

    private List<GeoData> parseLines(CsvParser parser, String... lines) throws IOException {
        Path file = directory.resolve("headers.csv");
        Files.write(file, List.of(lines));
        return parser.parse(file.toFile());
    }
}
