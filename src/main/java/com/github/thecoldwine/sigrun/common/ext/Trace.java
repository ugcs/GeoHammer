package com.github.thecoldwine.sigrun.common.ext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.github.thecoldwine.sigrun.common.TraceHeader;

public class Trace {
    
	private final byte[] binHeader;
    private final TraceHeader header;
    
    private float[] originalvalues;
    private float[] normvalues;
    
    private LatLon latLon;
    private LatLon latLonOrigin;
    private boolean end = false;
    
    //tmp for loading
    private boolean marked = false;
    
    //meters
    private double prevDist = 100000;
    
    public int maxindex;
    public int verticalOffset;
    
    private int indexInFile;
    private int indexInSet;
    
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
    
    private SgyFile file;
    
    public SgyFile getFile() {
        return file;
    }

    public void setFile(SgyFile file) {
        this.file = file;
    }

    private Trace(SgyFile file, byte[] binHeader, TraceHeader header) {
        this.file = file;
        this.binHeader = binHeader;
        this.header = header;
    }

    public Trace(SgyFile file, byte[] binHeader, TraceHeader header, float[] originalvalues, LatLon latLon) {
        
    	this.file = file;
        this.header = header;
        this.binHeader = binHeader; 
        this.originalvalues = originalvalues;
        this.latLon = latLon;
        this.latLonOrigin = latLon;
        
        this.good = new byte[originalvalues.length];
        this.edge = new byte[originalvalues.length];
    }

    public Trace copy() {
        return copy(file);
    }

    public Trace copy(SgyFile newFile) {
        byte[] newBinHeader = null;
        TraceHeader newHeader = null;
        if (binHeader != null) {
            newBinHeader = Arrays.copyOf(binHeader, binHeader.length);
            newHeader = GprFile.traceHeaderReader.read(newBinHeader);
        }
        Trace copy = new Trace(newFile, newBinHeader, newHeader);

        if (originalvalues != null) {
            copy.originalvalues = Arrays.copyOf(originalvalues, originalvalues.length);
        }
        if (normvalues != null) {
            copy.normvalues = Arrays.copyOf(normvalues, normvalues.length);
        }
        if (latLon != null) {
            copy.latLon = new LatLon(latLon.getLatDgr(), latLon.getLonDgr());
        }
        if (latLonOrigin != null) {
            copy.latLonOrigin = new LatLon(latLonOrigin.getLatDgr(), latLonOrigin.getLonDgr());;
        }
        copy.end = end;
        copy.marked = marked;
        copy.prevDist = prevDist;
        copy.maxindex = maxindex;
        copy.verticalOffset = verticalOffset;
        copy.indexInFile = indexInFile;
        copy.indexInSet = indexInSet;
        if (good != null) {
            copy.good = Arrays.copyOf(good, good.length);
        }
        if (edge != null) {
            copy.edge = Arrays.copyOf(edge, edge.length);
        }
        if (max != null) {
            copy.max = new HashSet<>(max);
        }
        return copy;
    }
    
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

	public boolean isEnd() {
		return end;
	}

	public void setEnd(boolean end) {
		this.end = end;
	}

	// in cm
	public double getPrevDist() {
		return prevDist;
	}

	// in cm
	public void setPrevDist(double prevDist) {
		this.prevDist = prevDist;
	}
    
	public byte[] getBinHeader() {
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

    public int getIndexInSet() {
        return indexInSet;
    }

    public void setIndexInSet(int indexInSet) {
        this.indexInSet = indexInSet;
    }
}
