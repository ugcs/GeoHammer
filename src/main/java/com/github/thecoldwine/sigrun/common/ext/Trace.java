package com.github.thecoldwine.sigrun.common.ext;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import org.jspecify.annotations.Nullable;

public class Trace {

    @Nullable
    private final byte[] binHeader;

    @Nullable
    private final TraceHeader header;
    
    private float[] originalValues;

    @Nullable
    private float[] normValues;
    
    private LatLon latLon;

    private LatLon latLonOrigin;

    //tmp for loading
    private boolean marked = false;
    
    //meters
    private double prevDist = 100000;
    
    private int maxindex;

    private int index;

    private byte[] good;

    /*
     * 0
     * 1 - 0 ('+' -> '-')
     * 2 - 0 ('-' -> '+')
     * 3 - min
     * 4 - max
     *  
     */
    private byte[] edge;
    
    public Trace(byte @Nullable [] binHeader, @Nullable TraceHeader header, float[] originalValues, LatLon latLon) {
        this.header = header;
        this.binHeader = binHeader; 
        this.originalValues = originalValues;
        this.latLon = latLon;
        this.latLonOrigin = latLon;
        
        this.good = new byte[originalValues.length];
        this.edge = new byte[originalValues.length];
    }

    @Nullable
    public TraceHeader getHeader() {
        return header;
    }
    
    public LatLon getLatLon() {
    	return latLon;
    }

    public LatLon getLatLonOrigin() {
    	return latLonOrigin;
    }

    public void setLatLon(LatLon latLon) {
		this.latLon = latLon;
	}
    
    public float[] getOriginalValues() {
    	return originalValues;
    }

    public void setOriginalValues(float[] vals) {
    	originalValues = vals;
    }
    
    public float[] getNormValues() {
    	return normValues != null ? normValues : originalValues;
    }

    public void setNormValues(float[] vals) {
    	normValues = vals;
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

    @Nullable
    public byte[] getBinHeader() {
		return binHeader;
	}

	public boolean isMarked() {
		return marked;
	}

	public void setMarked(boolean marked) {
		this.marked = marked;
	}

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public byte[] getGood() {
        return good;
    }

    public void setGood(byte[] good) {
        this.good = good;
    }

    public byte[] getEdge() {
        return edge;
    }

    public void setEdge(byte[] edge) {
        this.edge = edge;
    }
}
