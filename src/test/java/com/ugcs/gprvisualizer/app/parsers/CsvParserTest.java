package com.ugcs.gprvisualizer.app.parsers;

import com.ugcs.gprvisualizer.app.parsers.exceptions.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ugcs.gprvisualizer.app.parsers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.parsers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.Template;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

class CsvParserTest extends BaseParsersTest {

    private CsvParser csvParser;
    private String oldFile;
    private String newFile;
    private Iterable<GeoCoordinates> coordinates;

    @BeforeEach
    void setUp() {
        csvParser = new CsvParser(null);
        oldFile = "path/to/oldFile";
        newFile = "path/to/newFile";
        coordinates = new ArrayList<>();
        // TODO: Add some GeoCoordinates to the coordinates list
    }

    @Test
    void validCsv() throws IOException {

        String path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template);

        try {
            parser.parse(Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position.csv").toAbsolutePath().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void columnsMissmatchingCsv() throws IOException {

        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template);

        assertThrows(ParseException.class, () -> {
            parser.parse(Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position-missed-headers.csv")
                    .toAbsolutePath().toString());
        });
    }

    @Test
    void missedDateCsv() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template);

        assertThrows(IncorrectDateFormatException.class, () -> {
            parser.parse(Paths.get(CSVTestDataFolder + "Missed-date-position.csv").toAbsolutePath().toString());
        });
    }

    @Test
    void validCsvWithoutHeaders() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplateWithoutHeaders.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template);

        try {
            parser.parse(Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position-no-headers.csv").toAbsolutePath()
                    .toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void invalidCSV() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplateWithoutHeaders.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template);

        // invalid csv also parcing if possible
        try {
            parser.parse(
                Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position-invalid.csv").toAbsolutePath().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }        
    }

    @Test
    void magdroneCSV() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlMagdroneFolder + "MagDroneValidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        Parser parser = new CsvParser(template);

        try {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagdroneFolder + "Magdrone.csv").toAbsolutePath().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void magdroneCSVWithInvalidTemplate() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlMagdroneFolder + "MagDroneInvalidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        var parser = new CsvParser(template);

        Assertions.assertThrows(Exception.class, () -> {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagdroneFolder + "Magdrone.csv").toAbsolutePath().toString());
        });
    }

    @Test
    void magArrow() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlMagarrowFolder + "MagArrowValidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template);

        try {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagarrowFolder + "MagArrow.csv").toAbsolutePath().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}