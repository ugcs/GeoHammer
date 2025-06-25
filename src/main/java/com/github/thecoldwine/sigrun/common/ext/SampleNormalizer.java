package com.github.thecoldwine.sigrun.common.ext;

import java.util.List;
import java.util.stream.IntStream;

public class SampleNormalizer {

    private float avg;

    private float reduceFactor;

    public void normalize(List<Trace> traces) {
        // only bottom half because top has big distortion
        avg = (float) traces.stream()
                .flatMapToDouble(t -> IntStream
                        .range(t.numValues() / 2, t.numValues())
                        .mapToDouble(i -> t.getOriginalValue(i)))
                .average()
                .getAsDouble();

        // dispersion around avg
        float finalAvg = avg;
        float dispersion = (float) traces.stream()
                .flatMapToDouble(t -> IntStream
                        .range(0, t.numValues())
                        .mapToDouble(i -> Math.abs(t.getOriginalValue(i) - finalAvg)))
                .average()
                .getAsDouble();

        reduceFactor = dispersion / 500;
        normalize(traces, avg, reduceFactor);

        System.out.println("AVG: " + avg +  "  DISPERSION: " + dispersion);
    }

    private void normalize(List<Trace> traces, float avg, float reduceFactor) {
        for (Trace trace : traces) {
            for (int i = 0; i < trace.numValues(); i++ ) {
                float normalized = (trace.getOriginalValue(i) - avg) / reduceFactor;
                trace.setOriginalValue(i, normalized);
            }
        }
    }

    public void back(List<Trace> traces) {
        for (Trace trace : traces) {
            for (int i = 0; i < trace.numValues(); i++ ) {
                float restored = trace.getOriginalValue(i) * reduceFactor + avg;
                trace.setOriginalValue(i, restored);
            }
        }
    }

    public void copyFrom(SampleNormalizer sampleNormalizer) {
        this.avg = sampleNormalizer.avg;
        this.reduceFactor = sampleNormalizer.reduceFactor;
    }
}
