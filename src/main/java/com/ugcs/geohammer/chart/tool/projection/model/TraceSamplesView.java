package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Check;

public class TraceSamplesView implements TraceSamples {

    private final TraceFile file;

    private final IndexRange range;

    private final int sampleOffset;

    private final int maxSamples;

    public TraceSamplesView(TraceFile file, IndexRange range, int sampleOffset) {
        Check.notNull(file);
        Check.notNull(range);
        Check.condition(sampleOffset >= 0);

        this.file = file;
        this.range = range;
        this.sampleOffset = sampleOffset;

        int maxSamples = 0;
        for (int i = 0; i < range.size(); i++) {
            maxSamples = Math.max(maxSamples, numSamples(i));
        }
        this.maxSamples = maxSamples;
    }

    @Override
    public int numTraces() {
        return range.size();
    }

    @Override
    public int numSamples(int traceIndex) {
        Trace trace = file.getTraces().get(range.from() + traceIndex);
        IndexRange sampleRange = trace.getSampleRange();
        int numSamples = sampleRange != null
                ? sampleRange.from() + sampleRange.size()
                : trace.numSamples();
        return Math.max(numSamples - sampleOffset, 0);
    }

    @Override
    public int maxSamples() {
        return maxSamples;
    }

    @Override
    public float getValue(int traceIndex, int sampleIndex) {
        Trace trace = file.getTraces().get(range.from() + traceIndex);
        IndexRange sampleRange = trace.getSampleRange();
        sampleIndex += sampleOffset; // sample index in the file
        if (sampleRange != null) {
            if (sampleIndex < sampleRange.from()) {
                return Float.NaN;
            }
            sampleIndex -= sampleRange.from();
        }
        return trace.getSample(sampleIndex);
    }
}
