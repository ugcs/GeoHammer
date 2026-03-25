package com.ugcs.geohammer.chart.tool.projection.math;

public interface GainFunction {

    // depth normalized to [0, 1] range
    float getGain(float depth);
}
