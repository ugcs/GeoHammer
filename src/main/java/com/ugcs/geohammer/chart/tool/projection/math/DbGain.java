package com.ugcs.geohammer.chart.tool.projection.math;

public class DbGain implements GainFunction {

    private final float minGain;

    private final float maxGain;

    public DbGain(double minGainDb, double maxGainDb) {
        minGain = dbToLinear(minGainDb);
        maxGain = dbToLinear(maxGainDb);
    }

    public float getGain(float depth) {
        return minGain + (maxGain - minGain) * depth;
    }

    private float dbToLinear(double db) {
        return (float)Math.pow(10.0, 0.05 * db);
    }
}
