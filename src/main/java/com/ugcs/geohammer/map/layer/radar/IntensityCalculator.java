package com.ugcs.geohammer.map.layer.radar;

import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.gpr.ProfileField;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.QuickSelect;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Component
public class IntensityCalculator {

    private final Model model;

    public IntensityCalculator(Model model) {
        this.model = model;
    }

    public double[] getIntensity(TraceFile file, RadarSettings radarSettings) {
        Check.notNull(file);
        Check.notNull(radarSettings);

        TraceFile.Statistics stats = file.getStatistics();
        List<Trace> traces = file.getTraces();
        int numTraces = traces.size();

        double[] intensity = new double[numTraces];

        GPRChart chart = model.getGprChart(file);
        if (chart == null) {
            return intensity;
        }

        ProfileField profile = chart.getField();
        IndexRange sampleRange = profile.getDepthRange();

        // peaks
        List<List<Peak>> peaks = new ArrayList<>(numTraces);
        float baseline = (float) stats.baseline();
        for (Trace trace : traces) {
            List<Peak> tracePeaks = findPeaks(trace, sampleRange, baseline);
            peaks.add(tracePeaks);
        }

        // scale
        Scale scale = radarSettings.isAutoGain()
                ? buildAutoScale(file, sampleRange, peaks)
                : buildManualScale(file, sampleRange, radarSettings);

        // intensity
        double hardThreshold = radarSettings.isAutoGain()
                ? radarSettings.getThreshold() * stats.dispersion()
                : 0;
        for (int i = 0; i < intensity.length; i++) {
            List<Peak> tracePeaks = peaks.get(i);
            intensity[i] = getMaxIntensity(tracePeaks, hardThreshold, scale);
        }
        return intensity;
    }

    private List<Peak> findPeaks(Trace trace, IndexRange sampleRange, float baseline) {
        List<Peak> peaks = new ArrayList<>();

        int numSamples = trace.numSamples();
        if (sampleRange.from() >= numSamples) {
            return peaks;
        }

        float amplitude = trace.getSample(sampleRange.from()) - baseline;
        int peakIndex = sampleRange.from();
        float peakAmplitude = amplitude;

        int to = Math.min(sampleRange.to(), numSamples);
        for (int i = sampleRange.from() + 1; i < to; i++) {
            float amplitudeBefore = amplitude;
            amplitude = trace.getSample(i) - baseline;
            boolean samePolarity = (amplitude > 0) == (amplitudeBefore > 0);
            if (!samePolarity) {
                peaks.add(new Peak(peakIndex, peakAmplitude));
                peakAmplitude = amplitude;
                peakIndex = i;
            } else if (Math.abs(amplitude) > Math.abs(peakAmplitude)) {
                peakAmplitude = amplitude;
                peakIndex = i;
            }
        }
        peaks.add(new Peak(peakIndex, peakAmplitude));
        return peaks;
    }

    // sampleIndex -> peakAmplitude[]
    private Map<Integer, List<Float>> groupBySample(List<List<Peak>> peaks) {
        Map<Integer, List<Float>> peaksBySample = new HashMap<>();
        for (List<Peak> tracePeaks : peaks) {
            for (Peak peak : tracePeaks) {
                peaksBySample.computeIfAbsent(peak.index(), k -> new ArrayList<>())
                        .add(Math.abs(peak.amplitude()));
            }
        }
        return peaksBySample;
    }

    private double getMaxIntensity(List<Peak> peaks, double hardThreshold, Scale scale) {
        double max = 0;
        for (Peak peak : peaks) {
            float amplitude = Math.abs(peak.amplitude());
            if (amplitude < hardThreshold) {
                continue;
            }

            int i = peak.index(); // sample index
            float scaled = Math.max(0, amplitude - scale.threshold(i)) * scale.factor(i);
            max = Math.max(max, scaled);
        }
        return Math.clamp(max, 0, 200);
    }

    private Scale buildManualScale(TraceFile file, IndexRange sampleRange, RadarSettings radarSettings) {
        TraceFile.Statistics stats = file.getStatistics();

        int maxSamples = file.maxSamples();
        float[] thresholds = new float[maxSamples];
        float[] factors = new float[maxSamples];

        double dispersion = stats.dispersion();
        double threshold = radarSettings.getThreshold() * dispersion;
        // gain increase per sample in dispersion units
        double gainFactor = (radarSettings.getBottomGain() - radarSettings.getTopGain()) / maxSamples;
        int to = Math.min(sampleRange.to(), maxSamples);
        for (int i = sampleRange.from(); i < to; i++) {
            thresholds[i] = (float)threshold;
            factors[i] = dispersion != 0.0
                    ? (float)((radarSettings.getTopGain() + gainFactor * i) / dispersion)
                    : 0f;
        }

        return new Scale(thresholds, factors);
    }

    private Scale buildAutoScale(TraceFile file, IndexRange sampleRange, List<List<Peak>> peaks) {
        Map<Integer, List<Float>> peaksBySample = groupBySample(peaks);

        int maxSamples = file.maxSamples();
        float[] thresholds = new float[maxSamples];
        float[] factors = new float[maxSamples];

        int to = Math.min(sampleRange.to(), maxSamples);
        for (int i = sampleRange.from(); i < to; i++) {
            List<Float> samplePeaks = peaksBySample.get(i);
            if (Nulls.isNullOrEmpty(samplePeaks)) {
                continue;
            }

            int numPeaks = samplePeaks.size();
            float median = getMedian(samplePeaks);
            float upper = getKthSmallest(samplePeaks, numPeaks * 98 / 100);
            float spread = upper - median;

            thresholds[i] = median;
            factors[i] = spread > 0f ? 100 / spread : 0;
        }

        return new Scale(thresholds, factors);
    }

    private float getMedian(List<Float> peaks) {
        ToDoubleFunction<Float> f = v -> v;

        int n = peaks.size();
        int m = n / 2;
        if (n % 2 == 1) {
            return QuickSelect.select(peaks, f, m);
        } else {
            // after select(m) everything before index m is <= it
            float high = QuickSelect.select(peaks, f, m);
            float low = QuickSelect.select(peaks.subList(0, m), f, m - 1);
            return (low + high) / 2f;
        }
    }

    private float getKthSmallest(List<Float> peaks, int k) {
        ToDoubleFunction<Float> f = v -> v;
        return QuickSelect.select(peaks, f, k);
    }

    record Peak(int index, float amplitude) {
    }

    record Scale(float[] thresholds, float[] factors) {

        float threshold(int depth) {
            return thresholds[depth];
        }

        float factor(int depth) {
            return factors[depth];
        }
    }
}
