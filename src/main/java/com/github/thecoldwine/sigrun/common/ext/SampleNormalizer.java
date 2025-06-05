package com.github.thecoldwine.sigrun.common.ext;

import java.util.List;
import java.util.stream.IntStream;

public class SampleNormalizer {

    private float avg;

    private float reduceFactor;

    public void normalize(List<Trace> traces) {
        // only bottom half because top has big distortion
        avg = (float) traces.stream()
            .flatMapToDouble(t -> IntStream.range(t.getOriginalValues().length / 2, t.getOriginalValues().length)
                .mapToDouble(i -> t.getOriginalValues()[i]))
            .average().getAsDouble();

        // dispersion around avg
        float finalAvg = avg;
        float dispersion = (float) traces.stream().flatMapToDouble(t ->
            IntStream.range(0, t.getOriginalValues().length)
                .mapToDouble(i -> Math.abs(t.getOriginalValues()[i] - finalAvg)))
            .average().getAsDouble();

        reduceFactor = dispersion / 500;
        normalize(traces, avg, reduceFactor);

        System.out.println("AVG: " + avg +  "  DISPERSION: " + dispersion);
    }

    private void normalize(List<Trace> traces, float avg, float reduceFactor) {
        traces.stream().forEach(t ->
        {
            for (int i = 0; i < t.getOriginalValues().length; i++ ) {
                t.getOriginalValues()[i] = (t.getOriginalValues()[i] - avg) / reduceFactor;
            }
        });
    }

    public void back(List<Trace> traces) {
        traces.stream().forEach(t ->
        {
            for (int i = 0; i < t.getOriginalValues().length; i++ ) {
                t.getOriginalValues()[i] = t.getOriginalValues()[i] * reduceFactor + avg ;
            }
        });
    }

    public void copyFrom(SampleNormalizer sampleNormalizer) {
        this.avg = sampleNormalizer.avg;
        this.reduceFactor = sampleNormalizer.reduceFactor;
    }
}
