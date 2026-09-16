package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.model.IndexRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaFilesTest {

    @TempDir
    Path directory;

    @Test
    void readMetaOf_withCurrentNaming_loadsMeta() throws IOException {
        File source = createFile("data.sgy");
        saveMeta(directory.resolve("data.sgy.geohammer"), new IndexRange(3, 11));

        Meta meta = MetaFiles.readMetaOf(source, MetaSchema.createSchema());

        assertNotNull(meta);
        assertEquals(new IndexRange(3, 11), meta.getSampleRange());
    }

    @Test
    void readMetaOf_withLegacyNaming_loadsMeta() throws IOException {
        File source = createFile("data.sgy");
        saveMeta(directory.resolve("data.geohammer"), new IndexRange(3, 11));

        Meta meta = MetaFiles.readMetaOf(source, MetaSchema.createSchema());

        assertNotNull(meta);
        assertEquals(new IndexRange(3, 11), meta.getSampleRange());
    }

    @Test
    void readMetaOf_withBothNamings_prefersCurrent() throws IOException {
        File source = createFile("data.sgy");
        saveMeta(directory.resolve("data.sgy.geohammer"), new IndexRange(3, 11));
        saveMeta(directory.resolve("data.geohammer"), new IndexRange(7, 9));

        Meta meta = MetaFiles.readMetaOf(source, MetaSchema.createSchema());

        assertNotNull(meta);
        assertEquals(new IndexRange(3, 11), meta.getSampleRange());
    }

    @Test
    void readMetaOf_withoutMetaFile_returnsNull() throws IOException {
        File source = createFile("data.sgy");

        assertNull(MetaFiles.readMetaOf(source, MetaSchema.createSchema()));
    }

    @Test
    void writeMetaOf_writesCurrentNaming() throws IOException {
        File source = createFile("data.sgy");

        Meta meta = new Meta(MetaSchema.createSchema());
        meta.setSampleRange(new IndexRange(3, 11));
        MetaFiles.writeMetaOf(source, meta);

        Meta saved = MetaFiles.readMetaOf(source, MetaSchema.createSchema());
        assertEquals(new IndexRange(3, 11), saved.getSampleRange());
    }

    @Test
    void resolveSources_withCurrentNaming_returnsSingleSource() throws IOException {
        createFile("data.csv");
        createFile("data.jpg");
        File metaFile = createFile("data.csv.geohammer");

        assertEquals(List.of(new File(directory.toFile(), "data.csv")),
                MetaFiles.resolveSources(metaFile));
    }

    @Test
    void resolveSources_withLegacyNaming_ordersByKnownExtensions() throws IOException {
        createFile("data.jpg");
        createFile("data.csv");
        createFile("data.sgy");
        File metaFile = createFile("data.geohammer");

        assertEquals(List.of(
                        new File(directory.toFile(), "data.sgy"),
                        new File(directory.toFile(), "data.csv"),
                        new File(directory.toFile(), "data.jpg")),
                MetaFiles.resolveSources(metaFile));
    }

    @Test
    void resolveSources_withLegacyNaming_ignoresBaseNameCase() throws IOException {
        createFile("DATA.sgy");
        File metaFile = createFile("data.geohammer");

        assertEquals(List.of(new File(directory.toFile(), "DATA.sgy")),
                MetaFiles.resolveSources(metaFile));
    }

    @Test
    void resolveSources_withMetaFileAsBaseName_ignoresIt() throws IOException {
        createFile("data.geohammer");
        File metaFile = createFile("data.geohammer.geohammer");

        assertTrue(MetaFiles.resolveSources(metaFile).isEmpty());
    }

    @Test
    void resolveSources_withMetaFileOnly_returnsEmpty() throws IOException {
        File metaFile = createFile("data.geohammer");

        assertTrue(MetaFiles.resolveSources(metaFile).isEmpty());
    }

    @Test
    void resolveMetaPath_withBothNamings_prefersCurrent() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.sgy.geohammer");
        createFile("data.geohammer");

        assertEquals(directory.resolve("data.sgy.geohammer"), MetaFiles.resolveMetaPath(source));
    }

    @Test
    void resolveMetaPath_withLegacyNamingOnly_moves() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.geohammer");

        assertEquals(directory.resolve("data.sgy.geohammer"), MetaFiles.resolveMetaPath(source));
    }

    @Test
    void resolveMetaPath_withoutMetaFile_returnsNull() throws IOException {
        File source = createFile("data.sgy");

        assertNull(MetaFiles.resolveMetaPath(source));
    }

    @Test
    void moveMeta_renamesFileToCurrentNaming() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.geohammer");

        MetaFiles.readMetaOf(source, MetaSchema.createSchema());

        assertTrue(Files.exists(directory.resolve("data.sgy.geohammer")));
        assertFalse(Files.exists(directory.resolve("data.geohammer")));
    }

    @Test
    void moveMeta_withCurrentMetaPresent_keepsBothFiles() throws IOException {
        File source = createFile("data.sgy");
        createFile("data.sgy.geohammer");
        createFile("data.geohammer");

        MetaFiles.readMetaOf(source, MetaSchema.createSchema());

        assertTrue(Files.exists(directory.resolve("data.sgy.geohammer")));
        assertTrue(Files.exists(directory.resolve("data.geohammer")));
    }

    @Test
    void moveMeta_withExtensionlessSource_keepsMeta() throws IOException {
        File source = createFile("data");
        createFile("data.geohammer");

        MetaFiles.readMetaOf(source, MetaSchema.createSchema());

        assertEquals(directory.resolve("data.geohammer"), MetaFiles.resolveMetaPath(source));
        assertTrue(Files.exists(directory.resolve("data.geohammer")));
    }

    private File createFile(String name) throws IOException {
        Path path = directory.resolve(name);
        Files.createFile(path);
        return path.toFile();
    }

    private void saveMeta(Path path, IndexRange sampleRange) throws IOException {
        Meta meta = new Meta(MetaSchema.createSchema());
        meta.setSampleRange(sampleRange);
        MetaFiles.writeMeta(meta, path);
    }
}
