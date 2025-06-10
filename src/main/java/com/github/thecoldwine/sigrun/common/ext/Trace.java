package com.github.thecoldwine.sigrun.common.ext;

import java.util.HashSet;
import java.util.Set;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import org.jspecify.annotations.Nullable;

public class Trace {

    private final byte @Nullable [] binHeader;
    @Nullable
    private final TraceHeader header;
    
    private float[] originalvalues;
    private float @Nullable [] normvalues;
    
    private LatLon latLon;
    private LatLon latLonOrigin;

    //tmp for loading
    private boolean marked = false;
    
    //meters
    private double prevDist = 100000;
    
    public int maxindex;
    public int verticalOffset;
    
    private int indexInFile;

    public byte[] good;

    /*
     * 0
     * 1 - 0 ('+' -> '-')
     * 2 - 0 ('-' -> '+')
     * 3 - min
     * 4 - max
     *  
     */
    public byte[] edge;
    
    public Set<Integer> max = new HashSet<>();
    
    public Trace(byte @Nullable [] binHeader, @Nullable TraceHeader header, float[] originalvalues, LatLon latLon) {
        this.header = header;
        this.binHeader = binHeader; 
        this.originalvalues = originalvalues;
        this.latLon = latLon;
        this.latLonOrigin = latLon;
        
        this.good = new byte[originalvalues.length];
        this.edge = new byte[originalvalues.length];
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

    public void setLatLon(LatLon latLon2) {
		this.latLon = latLon2;
		
	}
    
    public float[] getOriginalValues() {
    	return originalvalues;
    }

    public void setOriginalValues(float[] vals) {
    	originalvalues = vals;
    }
    
    public float[] getNormValues() {
    	return normvalues != null ? normvalues : originalvalues;
    }

    public void setNormValues(float[] vals) {
    	normvalues = vals;
    }

	// in cm
	public double getPrevDist() {
		return prevDist;
	}

	// in cm
	public void setPrevDist(double prevDist) {
		this.prevDist = prevDist;
	}

    public byte @Nullable [] getBinHeader() {
		return binHeader;
	}

	public boolean isMarked() {
		return marked;
	}

	public void setMarked(boolean marked) {
		this.marked = marked;
	}

    public int getIndexInFile() {
        return indexInFile;
    }

    public void setIndexInFile(int indexInFile) {
        this.indexInFile = indexInFile;
    }
}
