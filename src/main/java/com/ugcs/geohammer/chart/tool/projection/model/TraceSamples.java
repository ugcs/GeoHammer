package com.ugcs.geohammer.chart.tool.projection.model;

public interface TraceSamples {

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

    float getValue(int traceIndex, int sampleIndex);
}
