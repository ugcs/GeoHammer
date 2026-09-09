package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.format.SampleStatistics;

public interface TraceSamples {

    // statistics of the emitted values, their baseline is always 0
    SampleStatistics getStatistics();

    int numTraces();

    int numSamples(int traceIndex);

    default int maxSamples() {
        int n = numTraces();
        int maxSamples = 0;
        for (int i = 0; i < n; i++) {
            maxSamples = Math.max(maxSamples, numSamples(i));
        }
        return maxSamples;
    }

    // relative to baseline
    float getValue(int traceIndex, int sampleIndex);
}
