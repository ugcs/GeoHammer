package com.ugcs.gprvisualizer.math;

import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Range;

import java.util.Collections;
import java.util.PriorityQueue;

public class AnalyticSignal {

    private final float[][] magnitudes;

    public AnalyticSignal(float[][] magnitudes) {
        Check.notNull(magnitudes);
        this.magnitudes = magnitudes;
    }

    public float[][] getMagnitudes() {
        return magnitudes;
    }

    public Range getRange(double percentile) {
        int m = magnitudes.length;
        int n = magnitudes[0].length;

        int total = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (!Float.isNaN(magnitudes[i][j])) {
                    total++;
                }
            }
        }

        int k = Math.max(1, (int) (percentile * total));

        // stores k min elements (max-heap of small values)
        PriorityQueue<Float> minHeap = new PriorityQueue<>(Collections.reverseOrder());
        // stores k max elements (min-heap of large values)
        PriorityQueue<Float> maxHeap = new PriorityQueue<>();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                float value = magnitudes[i][j];
                if (Float.isNaN(value)) {
                    continue;
                }

                minHeap.offer(value);
                if (minHeap.size() > k) {
                    minHeap.poll();
                }

                maxHeap.offer(value);
                if (maxHeap.size() > k) {
                    maxHeap.poll();
                }
            }
        }

        return minHeap.isEmpty() || maxHeap.isEmpty()
                ? new Range(0f, 0f)
                : new Range(minHeap.peek(), maxHeap.peek());
    }
}