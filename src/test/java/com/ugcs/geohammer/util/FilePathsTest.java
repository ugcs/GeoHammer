package com.ugcs.geohammer.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilePathsTest {

    @Test
    void skipsPathsThatDoNotExist(@TempDir Path dir) throws IOException {
        Path file = Files.createFile(dir.resolve("data.geohammer"));

        List<File> files = FilePaths.existingFiles(List.of(
                file.toString(),
                dir.resolve("removed.geohammer").toString()));

        assertEquals(List.of(file.toFile()), files);
    }

    @Test
    void resolvesRelativePaths() {
        List<File> files = FilePaths.existingFiles(List.of("."));

        assertEquals(1, files.size());
        assertTrue(files.getFirst().isAbsolute());
    }

    @Test
    void acceptsEmptyInput() {
        assertEquals(List.of(), FilePaths.existingFiles(List.of()));
    }
}
