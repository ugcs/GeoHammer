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

    private static final String SOURCE_META_CONTENT = "{\"lines\":[]}";

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

        assertEquals(copiesPath.resolve(DemoFile.GPR.getFileName()), copy.toPath());
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
    void createCopy_copiesSourceMeta() throws IOException {
        Path source = createSource(DemoFile.GPR);
        Files.writeString(source.resolveSibling(source.getFileName() + ".geohammer"), SOURCE_META_CONTENT);

        File copy = demoFiles.createCopy(DemoFile.GPR);

        Path copyMeta = copy.toPath().resolveSibling(copy.getName() + ".geohammer");
        assertEquals(SOURCE_META_CONTENT, Files.readString(copyMeta));
    }

    @Test
    void createCopy_withoutSourceMeta_createsNoMeta() throws IOException {
        createSource(DemoFile.GPR);

        File copy = demoFiles.createCopy(DemoFile.GPR);

        assertFalse(Files.exists(copy.toPath().resolveSibling(copy.getName() + ".geohammer")));
    }

    @Test
    void createCopy_replacesEditedMeta() throws IOException {
        Path source = createSource(DemoFile.GPR);
        Files.writeString(source.resolveSibling(source.getFileName() + ".geohammer"), SOURCE_META_CONTENT);
        File copy = demoFiles.createCopy(DemoFile.GPR);
        Path copyMeta = copy.toPath().resolveSibling(copy.getName() + ".geohammer");
        Files.writeString(copyMeta, "edited");

        demoFiles.createCopy(DemoFile.GPR);

        assertEquals(SOURCE_META_CONTENT, Files.readString(copyMeta));
    }

    @Test
    void createCopy_removesOtherDemoCopy() throws IOException {
        createSource(DemoFile.GPR);
        createSource(DemoFile.CSV);
        File csvCopy = demoFiles.createCopy(DemoFile.CSV);

        demoFiles.createCopy(DemoFile.GPR);

        assertFalse(Files.exists(csvCopy.toPath()));
    }

    @Test
    void createCopy_missingSource_keepsPreviousCopy() throws IOException {
        createSource(DemoFile.GPR);
        File gprCopy = demoFiles.createCopy(DemoFile.GPR);

        assertThrows(NoSuchFileException.class, () -> demoFiles.createCopy(DemoFile.CSV));

        assertEquals(SOURCE_CONTENT, Files.readString(gprCopy.toPath()));
    }

    @Test
    void createCopy_missingSource_throws() {
        assertThrows(NoSuchFileException.class, () -> demoFiles.createCopy(DemoFile.CSV));
    }

    private Path createSource(DemoFile demoFile) throws IOException {
        return Files.writeString(sourcesPath.resolve(demoFile.getFileName()), SOURCE_CONTENT);
    }
}
