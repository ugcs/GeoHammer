package com.ugcs.geohammer.format;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.math.filter.LazyRunningMedian;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Nulls;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

public class HorizontalProfile {

	private Color color = Color.red;

    // altitudes in a global traces frame
    private double[] altitudes;

    private double[] ellipsoidalHeights;

    // surface sample indices in a global traces frame
    private int[] surface;

	// horizontal trace offset applied to profile
	private int traceOffset = 0;

    // vertical sample offset
    private int sampleOffset = 0;

    private boolean detectPeaks;

    private int peakWindow = 8;

    private int surfaceFilterWindow = 25;

    public HorizontalProfile() {
        this.altitudes = new double[0];
        this.ellipsoidalHeights = new double[0];
        this.surface = new int[0];
	}

    public void setAltitudes(double[] altitudes) {
        this.altitudes = altitudes;
    }

    public void setEllipsoidalHeights(double[] ellipsoidalHeights) {
        this.ellipsoidalHeights = ellipsoidalHeights;
    }

    public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public int getTraceOffset() {
		return traceOffset;
	}

	public void setTraceOffset(int traceOffset) {
		this.traceOffset = traceOffset;
	}

    public int getSampleOffset() {
        return sampleOffset;
    }

    public void setSampleOffset(int sampleOffset) {
        this.sampleOffset = sampleOffset;
    }

    public boolean isDetectPeaks() {
        return detectPeaks;
    }

    public void setDetectPeaks(boolean detectPeaks) {
        this.detectPeaks = detectPeaks;
    }

    public int getPeakWindow() {
        return peakWindow;
    }

    public void setPeakWindow(int peakWindow) {
        this.peakWindow = peakWindow;
    }

    public int getSurfaceFilterWindow() {
        return surfaceFilterWindow;
    }

    public void setSurfaceFilterWindow(int surfaceFilterWindow) {
        this.surfaceFilterWindow = surfaceFilterWindow;
    }

	public int getSurfaceIndex(TraceFile traceFile, int traceIndex) {
        MetaFile metaFile = null;
        if (traceFile != null) {
            metaFile = traceFile.getMetaFile();
        }
        // global trace index
        int i = metaFile != null
                ? metaFile.getTraceIndex(traceIndex)
                : traceIndex;

		int surfaceIndex = i >= 0 && i < surface.length ? surface[i] : 0;
        if (metaFile == null) {
            return surfaceIndex;
        }
        IndexRange sampleRange = metaFile.getSampleRange();
        if (sampleRange == null) {
            return surfaceIndex;
        }
        surfaceIndex = Math.clamp(surfaceIndex, sampleRange.from(), sampleRange.to() - 1);
        return surfaceIndex - sampleRange.from();
	}

    public void buildSurface(TraceFile traceFile) {
        // num samples in a meter
        double samplesPerMeter = traceFile.getSamplesPerMeter();

        // init surface array
        int n = altitudes.length;
        surface = new int[n];
        for (int i = Math.max(0, traceOffset); i < Math.min(n, n + traceOffset); i++) {
            double altitude = altitudes[i - traceOffset];
            surface[i] = (int) (altitude * samplesPerMeter) + sampleOffset;
        }
        if (detectPeaks) {
            if (peakWindow > 0) {
                detectPeaks(traceFile, surface);
            }
            if (surfaceFilterWindow > 0) {
                medianFilter(surface);
            }
        }
    }

    private void detectPeaks(TraceFile traceFile, int[] surface) {
        List<Trace> traces = traceFile.getFileTraces();
        int n = Math.min(surface.length, traces.size());

        // find peaks with both polarities
        int[][] peaks = new int[n][];
        double bipolarSum = 0;
        for (int i = 0; i < n; i++) {
            float[] samples = traces.get(i).getFileSamples();
            peaks[i] = detectPeak(samples, surface[i]);
            // check polarity
            int min = peaks[i][0];
            int max = peaks[i][1];
            if (samples[max] > 0) {
                bipolarSum += samples[max];
            }
            if (samples[min] < 0) {
                bipolarSum += samples[min];
            }
        }
        int polarity = bipolarSum >= 0 ? 1 : -1; // peek strongest polarity
        for (int i = 0; i < n; i++) {
            surface[i] = polarity < 0 ? peaks[i][0] : peaks[i][1];
        }
    }

    // returns arrays of two elements: indices of the samples
    // with min and max amplitudes in a given window
    private int[] detectPeak(float[] samples, int center) {
        // w - scan window (from both sides of center)
        int w = peakWindow;
        // max distance penalty
        float distancePenalty = 0.2f;

        int n = samples.length;
        int start = Math.max(0, center - w);
        int end = Math.min(n - 1, center + w);

        float maxScore = -Float.MAX_VALUE;
        float minScore = Float.MAX_VALUE;
        int max = center;
        int min = center;
        for (int i = start; i <= end; i++) {
            float amplitude = samples[i];
            float distanceRatio = w > 1 ? Math.abs(i - center) / (float)w : 0f;
            float score = amplitude * (1 - distancePenalty * distanceRatio);
            if (score > maxScore) {
                maxScore = score;
                max = i;
            }
            if (score < minScore) {
                minScore = score;
                min = i;
            }
        }
        return new int[] {min, max};
    }

    private void medianFilter(int[] surface) {
        int w = surfaceFilterWindow;
        int n = surface.length;
        // number of window elements on the right from current
        // (including current element)
        int h = Math.min((w + 1) / 2, n);
        LazyRunningMedian runningMedian = new LazyRunningMedian(w);
        for (int i = 0; i < h - 1; i++) {
            runningMedian.add(surface[i]);
        }
        for (int i = 0; i < n; i++) {
            int next = i + h - 1;
            if (next < n) {
                runningMedian.add(surface[next]);
            } else {
                runningMedian.growWindow(-1);
            }
            Number median = runningMedian.median();
            surface[i] = median.intValue();
        }
    }

    public void flattenSurface(TraceFile traceFile, boolean removeAirGap) {
        List<Trace> traces = traceFile.getFileTraces();
        int n = Math.min(surface.length, traces.size());

        // highest surface sample
        int level = Integer.MAX_VALUE;
        double levelAltitude = 0.0;
        for (int i = 0; i < n; i++) {
            if (surface[i] < level) {
                level = surface[i];
                if (altitudes != null && i < altitudes.length) {
                    levelAltitude = altitudes[i];
                }
            }
        }
        // offset samples
        for (int i = 0; i < n; i++) {
            Trace trace = traces.get(i);
            float[] samples = trace.getFileSamples();
            int offset = surface[i] - level;
            if (offset > 0) {
                System.arraycopy(
                        samples, offset,
                        samples, 0,
                        samples.length - offset);
                Arrays.fill(
                        samples,
                        samples.length - offset,
                        samples.length,
                        0f);
            }
        }
        // crop air
        if (removeAirGap && level > 0) {
            removeAirGap(traceFile, level);
        }
        // warite syrface elevation
        updateElevations(traceFile, levelAltitude);

        traceFile.updateEdges();
        traceFile.setGroundProfile(null);
        traceFile.setUnsaved(true);
    }

    private void removeAirGap(TraceFile traceFile, int numSamples) {
        MetaFile metaFile = traceFile.getMetaFile();
        if (metaFile == null) {
            return; // operation requires meta
        }
        IndexRange sampleRange = metaFile.getSampleRange();
        if (sampleRange == null) {
            int maxSamples = 0;
            for (Trace trace : Nulls.toEmpty(traceFile.getFileTraces())) {
                maxSamples = Math.max(maxSamples, trace.getFileSamples().length);
            }
            sampleRange = new IndexRange(0, maxSamples);
        }
        sampleRange = new IndexRange(
                Math.max(sampleRange.from(), numSamples),
                Math.max(sampleRange.to(), numSamples + 1));
        metaFile.setSampleRange(sampleRange);
        traceFile.syncMeta();
    }

    private void updateElevations(TraceFile traceFile, double levelAltitude) {
        if (altitudes == null) {
            return;
        }
        List<Trace> traces = traceFile.getFileTraces();
        int n = Math.min(altitudes.length, traces.size());
        for (int i = 0; i < n; i++) {
            Trace trace = traces.get(i);
            double elevation = ellipsoidalHeights != null && i < ellipsoidalHeights.length
                    ? ellipsoidalHeights[i]
                    : trace.getReceiverAltitude();
            elevation -= (altitudes[i] - levelAltitude);
            trace.setReceiverAltitude((float)elevation);
        }
    }
}
