package com.ugcs.gprvisualizer.app.fir;

import java.util.ArrayList;
import java.util.List;

/**
 * The FIRFilter class represents a Finite Impulse Response (FIR) filter.
 *
 * The filter is designed based on the specified filter order, cutoff frequency, and sample rate.
 * It can be used to filter a single value or a list of values.
 *
 * The filter coefficients are computed using the createFIRCoefficients method, which uses a Hamming window function.
 * The filter implementation uses a circular buffer to store the input values and the coefficients are multiplied with the values in the buffer to get the filtered result.
 * The buffer is updated with the new input value and the oldest value is overwritten when the buffer is full.
 *
 * The FIRFilter class also provides a method to calculate the sampling rate based on a list of timestamps.
 * It uses the average interval between the timestamps to compute the sampling rate.
 *
 */
public class FIRFilter {
    
    private double[] coefficients;
    private double[] buffer;
    private int bufferIndex;

    /**
     * Constructs a new FIR filter with the specified filter order, cutoff frequency, and sample rate.
     *
     * @param filterOrder the filter order
     */
    public FIRFilter(int filterOrder) {
        this.coefficients = createFIRCoefficients(filterOrder);
        this.buffer = new double[filterOrder];
        this.bufferIndex = 0;
    }

    private double[] createFIRCoefficients(int filterOrder) {
        double[] coefficients = new double[filterOrder];
        double[] window = createHammingWindow(filterOrder);

        // Normalized cut-off frequency
        double normalizedCutoff = 2.0 / filterOrder;
        int m = filterOrder / 2;

        for (int i = 0; i < filterOrder; i++) {
            int n = i - m;
            if (n == 0) {
                coefficients[i] = normalizedCutoff;
            } else {
                coefficients[i] = normalizedCutoff * (Math.sin(Math.PI * normalizedCutoff * n) / (Math.PI * normalizedCutoff * n));
            }
            // Apply window function
            coefficients[i] *= window[i];
        }

        return coefficients;
    }

    private double[] createHammingWindow(int size) {
        double[] window = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (size - 1));
        }
        return window;
    }

    public List<Number> filterList(List<Number> data) {

        var paddingLength = coefficients.length;
        for (int i = 0; i < paddingLength; i++) {
            var value = data.get(paddingLength - 1 - i);
            if (value != null) {
                filter(value.doubleValue());
            } else {
                filter(null);
            }
        }

        List<Number> result = new ArrayList<>();
        for (Number value : data) {
            if (value != null) {
                result.add(filter(value.doubleValue()));
            } else {
                result.add(filter(null));
            }
        }

        for (int i = 0; i < paddingLength; i++) {
            var value = data.get(data.size() - paddingLength - 1 - i);
            if (value != null) {
                result.add(filter(value.doubleValue()));
            } else {
                result.add(filter(null));
            }
        }
        return result;
    }

    private Double filter(Double inputValue) {
        if (inputValue == null) {
            inputValue = Double.NaN;
        }
        buffer[bufferIndex] = inputValue;
        double result = 0.0;
        int coefIndex = 0;
        double coefSum = 0.0;
        for (int i = bufferIndex; i >= 0; i--) {
            if (!Double.isNaN(buffer[i])) {
                result += coefficients[coefIndex] * buffer[i];
                coefSum += coefficients[coefIndex];
            }
            coefIndex++;
        }
        for (int i = buffer.length - 1; i > bufferIndex; i--) {
            if (!Double.isNaN(buffer[i])) {
                result += coefficients[coefIndex] * buffer[i];
                coefSum += coefficients[coefIndex];
            }
            coefIndex++;
        }
        bufferIndex++;
        if (bufferIndex == buffer.length) {
            bufferIndex = 0;
        }
        return coefSum != 0 ? result / coefSum : null;
    }
}