package com.ugcs.gprvisualizer.app.parcers;

import com.ugcs.gprvisualizer.app.parcers.exceptions.CSVParsingException;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.parcers.csv.MagDroneCsvParser;
import com.ugcs.gprvisualizer.app.parcers.csv.NmeaCsvParser;
import com.ugcs.gprvisualizer.app.parcers.exceptions.IncorrectDateFormatException;
import com.ugcs.gprvisualizer.app.yaml.Template;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.framework.junit5.ApplicationExtension;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
class CsvParserTest extends BaseParsersTest {

    private CsvParser csvParser;
    private String oldFile;
    private String newFile;
    private Iterable<GeoCoordinates> coordinates;

	@Mock
	private PrefSettings prefSettings;

    @BeforeEach
    void setUp() {
        csvParser = new CsvParser(null, prefSettings);
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
        CsvParser parser = new CsvParser(template, prefSettings);

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
        CsvParser parser = new CsvParser(template, prefSettings);

        assertThrows(CSVParsingException.class, () -> {
            parser.parse(Paths.get(CSVTestDataFolder + "2020-07-29-14-37-42-position-missed-headers.csv")
                    .toAbsolutePath().toString());
        });
    }

    @Test
    void missedDateCsv() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template, prefSettings);

        assertThrows(IncorrectDateFormatException.class, () -> {
            parser.parse(Paths.get(CSVTestDataFolder + "Missed-date-position.csv").toAbsolutePath().toString());
        });
    }

    @Test
    void validCsvWithoutHeaders() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + "ValidCsvTemplateWithoutHeaders.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template, prefSettings);

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
        CsvParser parser = new CsvParser(template, prefSettings);

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
        Parser parser = new MagDroneCsvParser(template, prefSettings);

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
        var parser = new MagDroneCsvParser(template, prefSettings);

        try {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagdroneFolder + "Magdrone.csv").toAbsolutePath().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void magArrow() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlMagarrowFolder + "MagArrowValidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        CsvParser parser = new CsvParser(template, prefSettings);

        try {
            parser.parse(
                    Paths.get(CSVTestDataFolder + YamlMagarrowFolder + "MagArrow.csv").toAbsolutePath().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void nmea() throws IOException {
        var path = YamlTestDataFolder + YamlCsvFolder + YamlNmeaFolder + "NmeaValidTemplate.yaml";
        String file = new String(Files.readAllBytes(Paths.get(path)));

        Template template = deserializer.load(file);
        var parser = new NmeaCsvParser(template, prefSettings);

        try {
            parser.parse(Paths.get(CSVTestDataFolder + YamlNmeaFolder + "2020-10-23-09-16-13-pergam-falcon.log")
                    .toAbsolutePath().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    void testCreateFileWithCorrectedCoordinates_fileDoesNotExist() {
        oldFile = "nonexistent/file/path";
        assertThrows(FileNotFoundException.class, () -> {
            csvParser.createFileWithCorrectedCoordinates(oldFile, newFile, coordinates);
        });
    }

    @Test
    void testCreateFileWithCorrectedCoordinates_templateNotSet() {
        oldFile = "existing/file/path";
        // TODO: Make sure the file at oldFile exists
        assertThrows(FileNotFoundException.class, () -> {
            csvParser.createFileWithCorrectedCoordinates(oldFile, newFile, coordinates);
        });
    }

    //@Test
    void testCreateFileWithCorrectedCoordinates_correctDictionary() throws FileNotFoundException {
        oldFile = "existing/file/path";
        newFile = "path/to/newFile";
        // TODO: Make sure the file at oldFile exists
        // TODO: Set the template on csvParser
        // TODO: Add some GeoCoordinates to the coordinates list with trace numbers
        Result result = csvParser.createFileWithCorrectedCoordinates(oldFile, newFile, coordinates);
        // TODO: Assert something about the result
    }

    //@Test
    void testCreateFileWithCorrectedCoordinates_incorrectDictionary() throws FileNotFoundException {
        oldFile = "existing/file/path";
        newFile = "path/to/newFile";
        // TODO: Make sure the file at oldFile exists
        // TODO: Set the template on csvParser
        // TODO: Add some GeoCoordinates to the coordinates list without trace numbers
        Result result = csvParser.createFileWithCorrectedCoordinates(oldFile, newFile, coordinates);
        // TODO: Assert something about the result
    }
}