package com.ugcs.geohammer.format;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.util.Check;

import java.util.List;

public record SampleStatistics(float baseline, float dispersion) {

    public static SampleStatistics compute(TraceFile file) {
        List<Trace> traces = Check.notNull(file).getTraces();

        double baseline = 0;
        long n = 0;
        for (Trace trace : traces) {
            int numSamples = trace.numSamples();
            // only bottom half because top has big distortion
            for (int i = numSamples / 2; i < numSamples; i++) {
                baseline += trace.getSample(i);
                n++;
            }
        }
        if (n > 0) {
            baseline /= n;
        }

        double dispersion = 0;
        n = 0;
        for (Trace trace : traces) {
            int numSamples = trace.numSamples();
            for (int i = 0; i < numSamples; i++) {
                dispersion += Math.abs(trace.getSample(i) - baseline);
                n++;
            }
        }
        if (n > 0) {
            dispersion /= n;
        }

        return new SampleStatistics((float) baseline, (float) dispersion);
    }
}
