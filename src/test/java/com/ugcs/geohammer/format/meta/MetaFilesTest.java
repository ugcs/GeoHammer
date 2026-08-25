package com.ugcs.geohammer.format.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaFilesTest {

    @TempDir
    Path directory;

    @Test
    void getMetaPath_keepsSourceExtension() {
        File source = directory.resolve("data.csv").toFile();

        assertEquals(directory.resolve("data.csv.geohammer"), MetaFiles.getMetaPath(source));
    }

    @Test
    void getLegacyMetaPath_replacesSourceExtension() {
        File source = directory.resolve("data.csv").toFile();

        assertEquals(directory.resolve("data.geohammer"), MetaFiles.getLegacyMetaPath(source));
    }

    @Test
    void getSources_withCurrentNaming_returnsSingleSource() throws IOException {
        createFile("data.csv");
        createFile("data.jpg");
        File metaFile = createFile("data.csv.geohammer");

        assertEquals(List.of(new File(directory.toFile(), "data.csv")),
                MetaFiles.getSources(metaFile));
    }

    @Test
    void getSources_withLegacyNaming_ordersByKnownExtensions() throws IOException {
        createFile("data.jpg");
        createFile("data.csv");
        createFile("data.sgy");
        File metaFile = createFile("data.geohammer");

        assertEquals(List.of(
                        new File(directory.toFile(), "data.sgy"),
                        new File(directory.toFile(), "data.csv"),
                        new File(directory.toFile(), "data.jpg")),
                MetaFiles.getSources(metaFile));
    }

    @Test
    void getSources_withLegacyNaming_ignoresBaseNameCase() throws IOException {
        createFile("DATA.sgy");
        File metaFile = createFile("data.geohammer");

        assertEquals(List.of(new File(directory.toFile(), "DATA.sgy")),
                MetaFiles.getSources(metaFile));
    }

    @Test
    void getSources_withMetaFileAsBaseName_ignoresIt() throws IOException {
        createFile("data.geohammer");
        File metaFile = createFile("data.geohammer.geohammer");

        assertTrue(MetaFiles.getSources(metaFile).isEmpty());
    }

    @Test
    void getSources_withMetaFileOnly_returnsEmpty() throws IOException {
        File metaFile = createFile("data.geohammer");

        assertTrue(MetaFiles.getSources(metaFile).isEmpty());
    }

    @Test
    void findMetaPath_withBothNamings_prefersCurrent() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.sgy.geohammer");
        createFile("data.geohammer");

        assertEquals(MetaFiles.getMetaPath(source), MetaFiles.findMetaPath(source));
    }

    @Test
    void findMetaPath_withLegacyNamingOnly_returnsLegacy() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.geohammer");

        assertEquals(MetaFiles.getLegacyMetaPath(source), MetaFiles.findMetaPath(source));
    }

    @Test
    void findMetaPath_withoutMetaFile_returnsNull() throws IOException {
        File source = createFile("data.sgy");

        assertNull(MetaFiles.findMetaPath(source));
    }

    @Test
    void migrateLegacyMeta_renamesFileToCurrentNaming() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.geohammer");

        MetaFiles.migrateLegacyMeta(source);

        assertTrue(Files.exists(MetaFiles.getMetaPath(source)));
        assertFalse(Files.exists(MetaFiles.getLegacyMetaPath(source)));
    }

    @Test
    void migrateLegacyMeta_withCurrentMetaPresent_keepsBothFiles() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.sgy.geohammer");
        createFile("data.geohammer");

        MetaFiles.migrateLegacyMeta(source);

        assertTrue(Files.exists(MetaFiles.getMetaPath(source)));
        assertTrue(Files.exists(MetaFiles.getLegacyMetaPath(source)));
    }

    @Test
    void migrateLegacyMeta_withExtensionlessSource_keepsMeta() throws IOException {
        File source = createFile("data");
        createFile("data.geohammer");

        MetaFiles.migrateLegacyMeta(source);

        assertEquals(MetaFiles.getLegacyMetaPath(source), MetaFiles.getMetaPath(source));
        assertTrue(Files.exists(MetaFiles.getMetaPath(source)));
    }

    @Test
    void deleteLegacyMeta_removesLegacyFile() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.geohammer");

        MetaFiles.deleteLegacyMeta(source);

        assertFalse(Files.exists(MetaFiles.getLegacyMetaPath(source)));
    }

    @Test
    void deleteLegacyMeta_withExtensionlessSource_keepsMeta() throws IOException {
        File source = createFile("data");
        createFile("data.geohammer");

        MetaFiles.deleteLegacyMeta(source);

        assertTrue(Files.exists(MetaFiles.getMetaPath(source)));
    }

    private File createFile(String name) throws IOException {
        Path path = directory.resolve(name);
        Files.createFile(path);
        return path.toFile();
    }
}
