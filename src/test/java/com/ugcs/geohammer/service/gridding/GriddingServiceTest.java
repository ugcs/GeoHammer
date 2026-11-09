package com.ugcs.geohammer.service.gridding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.csv.parser.BaseParsersTest;
import com.ugcs.geohammer.model.template.Template;

class GriddingServiceTest extends BaseParsersTest {

    private static final String TEMPLATE_PATH = YamlTestDataFolder + YamlCsvFolder + "MissingValuesTemplate.yaml";

    private static final String FILE_PATH = CSVTestDataFolder + "missing-values.csv";

    private static final String SERIES = "IntCol";

    private static final GriddingParams PARAMS = new GriddingParams(100, 200);

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

    private CsvFile openFile() throws IOException {
        Template template = deserializer.load(Files.readString(Path.of(TEMPLATE_PATH)));
        template.init();
        CsvFile file = new CsvFile(null);
        file.open(new File(FILE_PATH), template);
        return file;
    }
}
