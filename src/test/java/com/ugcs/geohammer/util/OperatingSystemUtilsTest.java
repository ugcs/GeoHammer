package com.ugcs.geohammer.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class OperatingSystemUtilsTest {

    private static final String NAME = "agent";

    @TempDir
    Path root;

    @Test
    void findsExecutableInLaterDirectory() throws IOException {
        Path empty = Files.createDirectory(root.resolve("empty"));
        Path bin = Files.createDirectory(root.resolve("bin"));
        Path executable = createExecutable(bin);

        Path missing = root.resolve("missing");
        assertEquals(executable, OperatingSystemUtils.findExecutable(NAME, List.of(missing, empty, bin)));
    }

    @Test
    void prefersEarlierDirectory() throws IOException {
        Path first = Files.createDirectory(root.resolve("first"));
        Path second = Files.createDirectory(root.resolve("second"));
        Path executable = createExecutable(first);
        createExecutable(second);

        assertEquals(executable, OperatingSystemUtils.findExecutable(NAME, List.of(first, second)));
    }

    @Test
    void findsExecutableByNameWithExtension() throws IOException {
        Path bin = Files.createDirectory(root.resolve("bin"));
        Path executable = Files.createFile(bin.resolve(NAME + ".cmd"));
        executable.toFile().setExecutable(true);

        assertEquals(executable, OperatingSystemUtils.findExecutable(NAME + ".cmd", List.of(bin)));
    }

    @Test
    void returnsNullWhenNotFound()throws IOException {
        Path bin = Files.createDirectory(root.resolve("bin"));
        // a directory with the executable name is not an executable
        Files.createDirectory(bin.resolve(getFileName()));

        assertNull(OperatingSystemUtils.findExecutable(NAME, List.of(bin)));
        assertNull(OperatingSystemUtils.findExecutable(NAME, List.of()));
    }

    @Test
    void skipsFileWithoutExecutePermission() throws IOException {
        assumeFalse(OperatingSystemUtils.isWindows());
        Path bin = Files.createDirectory(root.resolve("bin"));
        Files.createFile(bin.resolve(getFileName()));

        assertNull(OperatingSystemUtils.findExecutable(NAME, List.of(bin)));
    }

    private static String getFileName() {
        return OperatingSystemUtils.isWindows() ? NAME + ".exe" : NAME;
    }

    private static Path createExecutable(Path directory) throws IOException {
        Path path = Files.createFile(directory.resolve(getFileName()));
        path.toFile().setExecutable(true);
        return path;
    }
}
