package com.ugcs.geohammer.format;

import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.meta.Meta;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizontalProfileTest {

    private static final File SOURCE = new File("src/test/resources/TestData/Parsers/Segy/2020-07-29-14-37-42-gpr.sgy");

    // far beyond any trace length
    private static final int FAR_SAMPLE_OFFSET = 1_000_000;

    private static final double FAR_ALTITUDE = 100_000.0;

    private static final int SPIKE_TRACE = 1;

    @Test
    void buildSurface_surfaceBelowTrace_detectsPeakNearBottom() throws IOException {
        GprFile file = openSource();
        HorizontalProfile profile = createDetectingProfile(file, FAR_SAMPLE_OFFSET);

        profile.buildSurface(file);

        List<Trace> traces = file.getTraces();
        for (int i = 0; i < traces.size(); i++) {
            int last = traces.get(i).getFileSamples().length - 1;
            int surfaceIndex = profile.getSurfaceIndex(file, i);
            assertTrue(surfaceIndex >= last - profile.getPeakWindow() && surfaceIndex <= last);
        }
    }

    @Test
    void buildSurface_surfaceAboveTrace_detectsPeakNearTop() throws IOException {
        GprFile file = openSource();
        HorizontalProfile profile = createDetectingProfile(file, -FAR_SAMPLE_OFFSET);

        profile.buildSurface(file);

        for (int i = 0; i < file.numTraces(); i++) {
            int surfaceIndex = profile.getSurfaceIndex(file, i);
            assertTrue(surfaceIndex >= 0 && surfaceIndex <= profile.getPeakWindow());
        }
    }

    @Test
    void flattenSurface_surfaceSpreadExceedsTrace_fillsTraceWithBaseline() throws IOException {
        GprFile file = openSource();
        double[] altitudes = new double[file.getFileTraces().size()];
        altitudes[SPIKE_TRACE] = FAR_ALTITUDE;
        HorizontalProfile profile = new HorizontalProfile();
        profile.setAltitudes(altitudes);
        profile.buildSurface(file);
        float baseline = file.getStatistics().baseline();

        profile.flattenSurface(file, false);

        for (float sample : file.getFileTraces().get(SPIKE_TRACE).getFileSamples()) {
            assertEquals(baseline, sample);
        }
    }

    private GprFile openSource() throws IOException {
        GprFile file = new GprFile();
        file.open(SOURCE);
        // report raw surface indices, not clamped to the visible sample range
        Meta meta = file.getMeta();
        assertNotNull(meta);
        meta.setSampleRange(null);
        return file;
    }

    private HorizontalProfile createDetectingProfile(GprFile file, int sampleOffset) {
        HorizontalProfile profile = new HorizontalProfile();
        profile.setAltitudes(new double[file.getFileTraces().size()]);
        profile.setSampleOffset(sampleOffset);
        profile.setDetectPeaks(true);
        return profile;
    }
}
