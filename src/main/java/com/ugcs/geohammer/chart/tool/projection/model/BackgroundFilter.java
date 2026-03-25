package com.ugcs.geohammer.chart.tool.projection.model;

public class BackgroundFilter implements TraceSamples {

    private final TraceSamples source;

    private final float[] means;

    public BackgroundFilter(TraceSamples source) {
        this.source = source;
        this.means = getSampleMeans(source);
    }

    private float[] getSampleMeans(TraceSamples samples) {
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
