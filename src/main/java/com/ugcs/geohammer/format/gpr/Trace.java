package com.ugcs.geohammer.format.gpr;

import java.time.Instant;
import java.util.Arrays;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import org.jspecify.annotations.Nullable;

public class Trace {

    private final @Nullable byte[] binHeader;

    private final @Nullable TraceHeader header;

    private int index;

    private @Nullable IndexRange sampleRange;

    private float[] samples;

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

	private Instant dateTime;

    //tmp for loading
    private boolean marked = false;
    
    //meters
    private double prevDist = 100000;
    
    private int maxindex;

    public Trace(byte @Nullable [] binHeader, @Nullable TraceHeader header,
				 float[] samples, LatLon latLon, Instant dateTime) {
        this.binHeader = binHeader;
        this.header = header;

        this.samples = samples;
        this.edges = new byte[samples.length];
        this.good = new byte[samples.length];

        this.latLonOrigin = latLon;
        this.latLon = latLon;
		this.dateTime = dateTime;
    }

    public Trace copy() {
        Trace copy = new Trace(
                binHeader,
                header,
                Arrays.copyOf(samples, samples.length),
                latLon,
				dateTime);
        copy.index = index;
        copy.sampleRange = sampleRange;
        copy.prevDist = prevDist;
        copy.maxindex = maxindex;
        copy.marked = marked;
        return copy;
    }

    public @Nullable byte[] getBinHeader() {
        return binHeader;
    }

    public @Nullable TraceHeader getHeader() {
        return header;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public @Nullable IndexRange getSampleRange() {
        return sampleRange;
    }

    public void setSampleRange(IndexRange sampleRange) {
        this.sampleRange = sampleRange;
    }

    private int localToGlobal(int index) {
        // local: index in range
        // global: index in samples
        if (sampleRange == null) {
            return index;
        }
        return index + sampleRange.from();
    }

    private int globalToLocal(int index) {
        if (sampleRange == null) {
            return index;
        }
        return index - sampleRange.from();
    }

    public int numSamples() {
        int totalSamples = samples.length;
        if (sampleRange == null) {
            return totalSamples;
        }
        return Math.clamp(sampleRange.to(), 0, totalSamples)
                - Math.clamp(sampleRange.from(), 0, totalSamples);
    }

    public float getSample(int index) {
        return samples[localToGlobal(index)];
    }

    public void setSample(int index, float value) {
        samples[localToGlobal(index)] = value;
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

	public Instant getDateTime() {
		return dateTime;
	}

	public void setDateTime(Instant dateTime) {
		this.dateTime = dateTime;
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
        return globalToLocal(maxindex);
    }

    public void setMaxIndex(int maxIndex) {
        this.maxindex = localToGlobal(maxIndex);
    }
}
