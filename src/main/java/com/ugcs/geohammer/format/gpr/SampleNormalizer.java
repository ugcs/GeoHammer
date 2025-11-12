package com.ugcs.geohammer.format.gpr;

import java.util.List;
import java.util.stream.IntStream;

public class SampleNormalizer {

    private float avg;

    private float reduceFactor;

    public void normalize(List<Trace> traces) {
        // only bottom half because top has big distortion
        avg = (float) traces.stream()
                .flatMapToDouble(t -> IntStream
                        .range(t.numSamples() / 2, t.numSamples())
                        .mapToDouble(i -> t.getSample(i)))
                .average()
                .getAsDouble();

        // dispersion around avg
        float finalAvg = avg;
        float dispersion = (float) traces.stream()
                .flatMapToDouble(t -> IntStream
                        .range(0, t.numSamples())
                        .mapToDouble(i -> Math.abs(t.getSample(i) - finalAvg)))
                .average()
                .getAsDouble();

        reduceFactor = dispersion / 500;
        normalize(traces, avg, reduceFactor);

        System.out.println("AVG: " + avg +  "  DISPERSION: " + dispersion);
    }

    private void normalize(List<Trace> traces, float avg, float reduceFactor) {
        for (Trace trace : traces) {
            for (int i = 0; i < trace.numSamples(); i++ ) {
                float normalized = (trace.getSample(i) - avg) / reduceFactor;
                trace.setSample(i, normalized);
            }
        }
    }

    public void back(List<Trace> traces) {
        for (Trace trace : traces) {
            for (int i = 0; i < trace.numSamples(); i++ ) {
                float restored = trace.getSample(i) * reduceFactor + avg;
                trace.setSample(i, restored);
            }
        }
    }

    public void copyFrom(SampleNormalizer sampleNormalizer) {
        this.avg = sampleNormalizer.avg;
        this.reduceFactor = sampleNormalizer.reduceFactor;
    }
}
