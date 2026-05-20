package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.GeoData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ugcs.geohammer.model.template.Template;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

class CsvParserTest extends BaseParsersTest {

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
    void warningsAggregateByColumn() throws IOException {
        String path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);
        parser.setHeaders(List.of("Elapsed", "Time", "Pitch", "Roll", "Yaw",
                "Latitude", "Longitude", "Altitude", "RTK Status", "ALT:Altitude", "GPR:Trace"));

        parser.parseDateTime(new String[]{"1", "badvalue1", "0", "0", "0",
                "1.0", "2.0", "0", "", "0", "0"});
        parser.parseDateTime(new String[]{"2", "badvalue2", "0", "0", "0",
                "1.0", "2.0", "0", "", "0", "0"});
        parser.parseDateTime(new String[]{"3", "badvalue3", "0", "0", "0",
                "1.0", "2.0", "0", "", "0", "0"});

        Collection<WarningGroup> warnings = parser.getWarnings();

        assertEquals(1, warnings.size());
        WarningGroup group = warnings.iterator().next();
        assertEquals("Time", group.column());
        assertEquals(3, group.count());
        assertTrue(group.message().contains("badvalue1"),
                "first message should be preserved as the example");
        assertFalse(group.message().contains("badvalue2"));
        assertFalse(group.message().contains("badvalue3"));
    }

    @Test
    void warningsSeparateDifferentColumns() throws IOException {
        String path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = loadTemplate(file);
        CsvParser parser = new CsvParser(template);
        parser.setHeaders(List.of("Elapsed", "Time", "Pitch", "Roll", "Yaw",
                "Latitude", "Longitude", "Altitude", "RTK Status", "ALT:Altitude", "GPR:Trace"));

        // bad Time (format) on one row
        parser.parseDateTime(new String[]{"1", "badtime", "0", "0", "0",
                "1.0", "2.0", "0", "", "0", "0"});

        Collection<WarningGroup> warnings = parser.getWarnings();

        assertEquals(1, warnings.size());
        WarningGroup group = warnings.iterator().next();
        assertEquals("Time", group.column());
        assertEquals(1, group.count());
    }
}