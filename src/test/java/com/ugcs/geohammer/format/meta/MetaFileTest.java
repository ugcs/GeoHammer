package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.model.IndexRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaFileTest {

    @TempDir
    Path directory;

    @Test
    void loadFor_withCurrentNaming_loadsMeta() throws IOException {
        File source = createFile("data.sgy");
        saveMeta(MetaFileNaming.getMetaPath(source), new IndexRange(3, 11));

        MetaFile metaFile = new MetaFile();

        assertTrue(metaFile.loadFor(source));
        assertEquals(new IndexRange(3, 11), metaFile.getSampleRange());
    }

    @Test
    void loadFor_withLegacyNaming_loadsMeta() throws IOException {
        File source = createFile("data.sgy");
        saveMeta(MetaFileNaming.getLegacyMetaPath(source), new IndexRange(3, 11));

        MetaFile metaFile = new MetaFile();

        assertTrue(metaFile.loadFor(source));
        assertEquals(new IndexRange(3, 11), metaFile.getSampleRange());
    }

    @Test
    void loadFor_withBothNamings_prefersCurrent() throws IOException {
        File source = createFile("data.sgy");
        saveMeta(MetaFileNaming.getMetaPath(source), new IndexRange(3, 11));
        saveMeta(MetaFileNaming.getLegacyMetaPath(source), new IndexRange(7, 9));

        MetaFile metaFile = new MetaFile();

        assertTrue(metaFile.loadFor(source));
        assertEquals(new IndexRange(3, 11), metaFile.getSampleRange());
    }

    @Test
    void loadFor_withoutMetaFile_returnsFalse() throws IOException {
        File source = createFile("data.sgy");

        assertFalse(new MetaFile().loadFor(source));
    }

    @Test
    void saveFor_writesCurrentNaming() throws IOException {
        File source = createFile("data.sgy");

        MetaFile metaFile = new MetaFile();
        metaFile.setSampleRange(new IndexRange(3, 11));
        metaFile.saveFor(source);

        MetaFile saved = new MetaFile();
        saved.load(MetaFileNaming.getMetaPath(source));
        assertEquals(new IndexRange(3, 11), saved.getSampleRange());
    }

    private File createFile(String name) throws IOException {
        Path path = directory.resolve(name);
        Files.createFile(path);
        return path.toFile();
    }

    private void saveMeta(Path path, IndexRange sampleRange) throws IOException {
        MetaFile metaFile = new MetaFile();
        metaFile.setSampleRange(sampleRange);
        metaFile.save(path);
    }
}
