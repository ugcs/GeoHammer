package com.ugcs.geohammer.format.gpr;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.meta.Meta;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.util.AuxElements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GprFileTest {

    private static final File SOURCE = new File("src/test/resources/TestData/Parsers/Segy/2020-07-29-14-37-42-gpr.sgy");

    private static final int MARK_BYTE_POS = 238;

    // more than a byte can count
    private static final int NUM_MARKS = 300;

    private static final int RANGE_FROM = 10;

    // traces of the first line, removed in meta
    private static final int REMOVED_TRACES = 20;

    private static final IndexRange SAMPLE_RANGE = new IndexRange(10, 100);

    private static final Set<Integer> META_MARKS = Set.of(0, 5, 42);

    private static final double POSITION_SHIFT = 0.001;

    private static final double POSITION_TOLERANCE = 1e-7;

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

    @Test
    void writePositionsToSource_editedMeta_changesOnlyPositions() throws IOException {
        File source = copyFixture();
        saveEditedMeta(source);

        GprFile edited = new GprFile();
        edited.open(source);
        List<BinFile.BinTrace> sourceTraces = BinFile.load(source).getTraces();

        // geotag as the geotagger does for a file that is not open
        GprFile file = new GprFile();
        file.open(source);
        List<LatLon> positions = shiftPositions(file);
        file.copyPositionsFromMeta();
        file.writePositionsToSource();

        List<BinFile.BinTrace> savedTraces = BinFile.load(source).getTraces();
        assertEquals(sourceTraces.size(), savedTraces.size());
        for (int i = 0; i < savedTraces.size(); i++) {
            assertArrayEquals(sourceTraces.get(i).data, savedTraces.get(i).data, "trace " + i);
        }
        for (int i = 0; i < REMOVED_TRACES; i++) {
            assertArrayEquals(sourceTraces.get(i).header, savedTraces.get(i).header, "trace " + i);
        }

        GprFile saved = new GprFile();
        saved.open(source);
        assertTrue(saved.isBackgroundRemoved());
        assertEquals(META_MARKS, AuxElements.getMarkIndices(saved.getAuxElements()));
        assertEquals(edited.numTraces(), saved.numTraces());
        for (int i = 0; i < saved.numTraces(); i++) {
            assertEquals(edited.getFileTraceIndex(i), saved.getFileTraceIndex(i), "trace " + i);

            Trace trace = saved.getTraces().get(i);
            assertEquals(positions.get(i).getLatDgr(), trace.getLatLon().getLatDgr(), POSITION_TOLERANCE, "trace " + i);
            assertEquals(positions.get(i).getLonDgr(), trace.getLatLon().getLonDgr(), POSITION_TOLERANCE, "trace " + i);
            assertEquals(SAMPLE_RANGE.size(), trace.numSamples(), "trace " + i);
            assertArrayEquals(edited.getTraces().get(i).getFileSamples(), trace.getFileSamples(), "trace " + i);
        }
    }

    @Test
    void writePositionsToSource_traceCountChanged_fails() throws IOException {
        File source = copyFixture();
        GprFile file = new GprFile();
        file.open(source);

        // the source is replaced by a shorter file after loading
        GprFile other = new GprFile();
        other.open(source);
        other.save(source, new IndexRange(0, RANGE_FROM));

        assertThrows(IOException.class, file::writePositionsToSource);
        assertEquals(RANGE_FROM, BinFile.load(source).getTraces().size());
    }

    private File copyFixture() throws IOException {
        Path target = directory.resolve(SOURCE.getName());
        Files.copy(SOURCE.toPath(), target);
        return target.toFile();
    }

    // removes the first line, crops samples, removes background and sets marks
    private void saveEditedMeta(File source) throws IOException {
        GprFile file = new GprFile();
        file.open(source);
        Meta meta = file.getMeta();
        assertTrue(meta != null && meta.numValues() > REMOVED_TRACES);

        List<TraceGeoData> values = meta.getValues();
        for (int i = 0; i < values.size(); i++) {
            values.get(i).setLine(i < REMOVED_TRACES ? 0 : 1);
        }
        meta.setValues(new ArrayList<>(values.subList(REMOVED_TRACES, values.size())));
        meta.setSampleRange(SAMPLE_RANGE);
        file.syncMeta();
        file.tracesChanged();
        file.removeBackground(null);

        file.getAuxElements().clear();
        for (int mark : META_MARKS) {
            file.getAuxElements().add(new FoundPlace(new TraceKey(file, mark), null));
        }
        file.saveMeta();
    }

    private List<LatLon> shiftPositions(GprFile file) {
        List<LatLon> positions = new ArrayList<>();
        for (GeoData value : file.getGeoData()) {
            LatLon latLon = new LatLon(value.getLatitude() + POSITION_SHIFT, value.getLongitude() + POSITION_SHIFT);
            value.setLatLon(latLon);
            positions.add(latLon);
        }
        return positions;
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
