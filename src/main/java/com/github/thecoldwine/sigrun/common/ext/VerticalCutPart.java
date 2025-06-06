package com.github.thecoldwine.sigrun.common.ext;

public class VerticalCutPart {

	// TODO GPR_LINES
	// always 0
	private int startTrace;
	// TODO GPR_LINES
	// always traces.size
	private int finishTrace;

	private int maxSamples;
	
	public int getTraces() {
		return finishTrace - startTrace;
	}
	
	public void setStartTrace(int startTrace) {
		this.startTrace = startTrace;	
	}
	
	public void setFinishTrace(int finishTrace) {
		this.finishTrace = finishTrace;
	}

	public int getMaxSamples() {
		return maxSamples;
	}

	public void setMaxSamples(int maxSamples) {
		this.maxSamples = maxSamples;
	}
}
