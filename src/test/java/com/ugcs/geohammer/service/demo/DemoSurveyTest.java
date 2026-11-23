package com.ugcs.geohammer.service.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// runs against the demo files shipped in the data folder of the project
class DemoSurveyTest {

    @TempDir
    Path otherPath;

    @Test
    void getFile_returnsShippedFiles() throws IOException {
        for (DemoSurvey demoSurvey : DemoSurvey.values()) {
            File file = demoSurvey.getFile();

            assertTrue(file.isFile());
            assertEquals(demoSurvey.getFileName(), file.getName());
        }
    }

    @Test
    void isDemoFile_shippedFile() throws IOException {
        assertTrue(DemoSurvey.isDemoFile(DemoSurvey.GPR.getFile()));
    }

    @Test
    void isDemoFile_pathWithParentReference() throws IOException {
        Path folder = DemoSurvey.CSV.getFile().toPath().getParent();
        File file = folder.resolve("..").resolve(folder.getFileName()).resolve(DemoSurvey.CSV.getFileName()).toFile();

        assertTrue(DemoSurvey.isDemoFile(file));
    }

    @Test
    void isDemoFile_fileInOtherFolder() {
        File file = otherPath.resolve(DemoSurvey.GPR.getFileName()).toFile();

        assertFalse(DemoSurvey.isDemoFile(file));
    }

    @Test
    void isDemoFile_otherFileInDemoFolder() throws IOException {
        Path folder = DemoSurvey.GPR.getFile().toPath().getParent();

        assertFalse(DemoSurvey.isDemoFile(folder.resolve("survey.csv").toFile()));
        assertFalse(DemoSurvey.isDemoFile(folder.resolve(DemoSurvey.GPR.getFileName() + ".geohammer").toFile()));
    }

    @Test
    void isDemoFile_fileInSubfolder() throws IOException {
        Path folder = DemoSurvey.GPR.getFile().toPath().getParent();
        File file = folder.resolve("nested").resolve(DemoSurvey.GPR.getFileName()).toFile();

        assertFalse(DemoSurvey.isDemoFile(file));
    }
}
