package com.ugcs.geohammer.format.gpr;

import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.util.AuxElements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GprFileTest {

    private static final File SOURCE = new File("src/test/resources/TestData/Parsers/Segy/2020-07-29-14-37-42-gpr.sgy");

    private static final int MARK_BYTE_POS = 238;

    // more than a byte can count
    private static final int NUM_MARKS = 300;

    private static final int RANGE_FROM = 10;

    @TempDir
    Path directory;

    @Test
    void save_manyMarks_keepsAllMarks() throws IOException {
        GprFile source = openSource();
        Set<Integer> marks = addMarks(source, 0);

        File target = directory.resolve("saved.sgy").toFile();
        source.save(target, new IndexRange(0, source.numTraces()));

        assertMarks(target, marks);
    }

    @Test
    void saveRange_manyMarks_keepsAllMarks() throws IOException {
        GprFile source = openSource();
        Set<Integer> marks = addMarks(source, RANGE_FROM);

        File target = directory.resolve("line.sgy").toFile();
        source.save(target, new IndexRange(RANGE_FROM, RANGE_FROM + NUM_MARKS));

        Set<Integer> rangeMarks = new HashSet<>();
        for (int mark : marks) {
            rangeMarks.add(mark - RANGE_FROM);
        }
        assertMarks(target, rangeMarks);
    }

    private GprFile openSource() throws IOException {
        GprFile file = new GprFile();
        file.open(SOURCE);
        assertTrue(file.numTraces() >= RANGE_FROM + NUM_MARKS);
        file.getAuxElements().clear();
        return file;
    }

    private Set<Integer> addMarks(GprFile file, int from) {
        Set<Integer> marks = new HashSet<>();
        for (int i = from; i < from + NUM_MARKS; i++) {
            file.getAuxElements().add(new FoundPlace(new TraceKey(file, i), null));
            marks.add(i);
        }
        return marks;
    }

    private void assertMarks(File target, Set<Integer> expected) throws IOException {
        GprFile saved = new GprFile();
        saved.open(target);
        assertEquals(expected, AuxElements.getMarkIndices(saved.getAuxElements()));

        List<BinFile.BinTrace> traces = BinFile.load(target).getTraces();
        for (int i = 0; i < traces.size(); i++) {
            int markValue = expected.contains(i) ? 1 : 0;
            assertEquals(markValue, traces.get(i).header[MARK_BYTE_POS], "trace " + i);
        }
    }
}
