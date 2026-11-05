package com.ugcs.geohammer.service.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoFilesTest {

    private static final String SOURCE_CONTENT = "original";

    @TempDir
    Path sourcesPath;

    @TempDir
    Path copiesPath;

    private DemoFiles demoFiles;

    @BeforeEach
    void setUp() {
        demoFiles = new DemoFiles(sourcesPath, copiesPath);
    }

    @Test
    void createCopy_copiesSourceToDemoFolder() throws IOException {
        Path source = createSource(DemoFile.GPR);

        File copy = demoFiles.createCopy(DemoFile.GPR);

        assertEquals(copiesPath.resolve("gpr").resolve(DemoFile.GPR.getFileName()), copy.toPath());
        assertEquals(SOURCE_CONTENT, Files.readString(copy.toPath()));
        assertEquals(SOURCE_CONTENT, Files.readString(source));
    }

    @Test
    void createCopy_replacesEditedCopyAndLeftovers() throws IOException {
        createSource(DemoFile.GPR);
        File copy = demoFiles.createCopy(DemoFile.GPR);
        Files.writeString(copy.toPath(), "edited");
        Path sidecar = copy.toPath().resolveSibling(copy.getName() + ".geohammer");
        Files.writeString(sidecar, "{}");

        File newCopy = demoFiles.createCopy(DemoFile.GPR);

        assertEquals(SOURCE_CONTENT, Files.readString(newCopy.toPath()));
        assertFalse(Files.exists(sidecar));
    }

    @Test
    void createCopy_keepsOtherDemoCopy() throws IOException {
        createSource(DemoFile.GPR);
        createSource(DemoFile.CSV);
        File csvCopy = demoFiles.createCopy(DemoFile.CSV);

        demoFiles.createCopy(DemoFile.GPR);

        assertEquals(SOURCE_CONTENT, Files.readString(csvCopy.toPath()));
    }

    @Test
    void createCopy_missingSource_throws() {
        assertThrows(NoSuchFileException.class, () -> demoFiles.createCopy(DemoFile.CSV));
    }

    private Path createSource(DemoFile demoFile) throws IOException {
        Path folder = Files.createDirectories(sourcesPath.resolve(demoFile.getFolder()));
        return Files.writeString(folder.resolve(demoFile.getFileName()), SOURCE_CONTENT);
    }
}
