package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.format.SampleStatistics;

public class BackgroundFilter implements TraceSamples {

    private final TraceSamples source;

    private final float[] means;

    private final SampleStatistics statistics;

    public BackgroundFilter(TraceSamples source) {
        this.source = source;
        this.means = getSampleMeans(source);
        this.statistics = getStatistics(source, means);
    }

    private static float[] getSampleMeans(TraceSamples samples) {
        int numTraces = samples.numTraces();
        int numSamples = samples.maxSamples();

        float[] means = new float[numSamples];
        for (int j = 0; j < numSamples; j++) {
            float sum = 0;
            int count = 0;
            for (int i = 0; i < numTraces; i++) {
                float value = samples.getValue(i, j);
                if (Float.isNaN(value)) {
                    continue;
                }
                sum += value;
                count++;
            }
            if (count > 0) {
                sum /= count;
            }
            means[j] = sum;
        }
        return means;
    }

    private static SampleStatistics getStatistics(TraceSamples samples, float[] means) {
        int numTraces = samples.numTraces();
        int numSamples = means.length;

        double dispersion = 0;
        long count = 0;
        for (int i = 0; i < numTraces; i++) {
            for (int j = 0; j < numSamples; j++) {
                float value = samples.getValue(i, j);
                if (Float.isNaN(value)) {
                    continue;
                }
                dispersion += Math.abs(value - means[j]);
                count++;
            }
        }
        if (count > 0) {
            dispersion /= count;
        }
        // removing the per-depth means centres every depth on zero
        return new SampleStatistics(0f, (float) dispersion);
    }

    @Override
    public SampleStatistics getStatistics() {
        return statistics;
    }

    @Override
    public int numTraces() {
        return source.numTraces();
    }

    @Override
    public int numSamples(int traceIndex) {
        return source.numSamples(traceIndex);
    }

    @Override
    public int maxSamples() {
        return source.maxSamples();
    }

    @Override
    public float getValue(int traceIndex, int sampleIndex) {
        float value = source.getValue(traceIndex, sampleIndex);
        if (Float.isNaN(value)) {
            return Float.NaN;
        }
        return value - means[sampleIndex];
    }
}
