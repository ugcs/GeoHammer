package com.ugcs.geohammer.service.gpr;

import java.util.List;

import com.ugcs.geohammer.format.gpr.Edge;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.model.event.WhatChanged;

public class EdgeFinder implements Command {

    public void execute(TraceFile traceFile, ProgressListener listener) {
        List<Trace> traces = traceFile.getTraces();
        for (Trace trace : traces) {
            int numSamples = trace.numSamples();
            if (numSamples == 0) {
                continue;
            }

            float baseline = trace.getAmplitudeBaseline();
            float amplitude = trace.getSample(0) - baseline;
            float peakAmplitude = amplitude;
            int peakIndex = 0;

            trace.setEdge(0, Edge.EMPTY);
            for (int i = 1; i < numSamples; i++) {
                float amplitudeBefore = amplitude;
                amplitude = trace.getSample(i) - baseline;

                if ((int) Math.signum(amplitude) != (int) Math.signum(amplitudeBefore)) {
                    trace.setEdge(i, amplitude < amplitudeBefore ? Edge.FALL_CROSS : Edge.RISE_CROSS);
                    trace.setEdge(peakIndex, peakAmplitude < 0 ? Edge.MIN_PEAK : Edge.MAX_PEAK);
                    peakAmplitude = amplitude;
                    peakIndex = i;
                } else {
                    trace.setEdge(i, Edge.EMPTY);
                }

                if (Math.abs(amplitude) > Math.abs(peakAmplitude)) {
                    peakAmplitude = amplitude;
                    peakIndex = i;
                }
            }
            trace.setEdge(peakIndex, peakAmplitude < 0 ? Edge.MIN_PEAK : Edge.MAX_PEAK);
        }
    }

    @Override
    public String getButtonText() {
        return "Scan for Edges";
    }

    @Override
    public WhatChanged.Change getChange() {
        return WhatChanged.Change.traceValues;
    }
}
