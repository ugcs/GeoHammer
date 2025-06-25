package com.github.thecoldwine.sigrun.common.ext;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public class Trace {

    @Nullable
    private final byte[] binHeader;

    @Nullable
    private final TraceHeader header;

    private int index;

    private float[] originalValues;

    private float[] normValues;

    /*
     * 0
     * 1 - 0 ('+' -> '-')
     * 2 - 0 ('-' -> '+')
     * 3 - min
     * 4 - max
     *
     */
    private byte[] edges;

    private byte[] good;

    private LatLon latLon;

    private LatLon latLonOrigin;

    //tmp for loading
    private boolean marked = false;
    
    //meters
    private double prevDist = 100000;
    
    private int maxindex;

    private SampleRange sampleRange;

    public Trace(byte @Nullable [] binHeader, @Nullable TraceHeader header,
                 float[] originalValues, LatLon latLon) {
        this.binHeader = binHeader;
        this.header = header;

        this.originalValues = originalValues;
        this.edges = new byte[originalValues.length];
        this.good = new byte[originalValues.length];

        this.latLonOrigin = latLon;
        this.latLon = latLon;
    }

    public Trace copy() {
        return new Trace(
                binHeader,
                header,
                Arrays.copyOf(originalValues, originalValues.length),
                latLon);
    }

    private int localToGlobal(int index) {
        if (sampleRange == null) {
            return index;
        }
        return index + sampleRange.getFrom();
    }

    private int globalToLocal(int index) {
        if (sampleRange == null) {
            return index;
        }
        return index - sampleRange.getFrom();
    }

    @Nullable
    public byte[] getBinHeader() {
        return binHeader;
    }

    @Nullable
    public TraceHeader getHeader() {
        return header;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int numValues() {
        if (sampleRange == null) {
            return originalValues.length;
        }
        return sampleRange.size();
    }

    public float getOriginalValue(int index) {
        return originalValues[localToGlobal(index)];
    }

    public void setOriginalValue(int index, float value) {
        originalValues[localToGlobal(index)] = value;
    }

    public float getValue(int index) {
        if (normValues == null) {
            return getOriginalValue(index);
        }
        return normValues[localToGlobal(index)];
    }

    public void setValue(int index, float value) {
        if (normValues == null) {
            normValues = Arrays.copyOf(originalValues, originalValues.length);
        }
        normValues[localToGlobal(index)] = value;
    }

    public byte getEdge(int index) {
        return edges[localToGlobal(index)];
    }

    public void setEdge(int index, byte value) {
        edges[localToGlobal(index)] = value;
    }

    public byte getGood(int index) {
        return good[localToGlobal(index)];
    }

    public void setGood(int index, byte value) {
        good[localToGlobal(index)] = value;
    }

    public LatLon getLatLon() {
        return latLon;
    }

    public void setLatLon(LatLon latLon) {
        this.latLon = latLon;
    }

    public LatLon getLatLonOrigin() {
        return latLonOrigin;
    }

    public boolean isMarked() {
        return marked;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }

	// in cm
	public double getPrevDist() {
		return prevDist;
	}

	// in cm
	public void setPrevDist(double prevDist) {
		this.prevDist = prevDist;
	}

    public int getMaxIndex() {
        return maxindex;
    }

    public void setMaxIndex(int maxIndex) {
        this.maxindex = maxindex;
    }

    public static class SampleRange {

        private final int from;

        private final int to; // exclusive

        public SampleRange(int from, int to) {
            this.from = from;
            this.to = to;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }

        public int size() {
            return to - from;
        }
    }
}
