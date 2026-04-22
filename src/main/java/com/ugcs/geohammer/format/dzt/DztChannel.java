package com.ugcs.geohammer.format.dzt;

import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.format.Channel;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.MinMaxAvg;

public class DztChannel extends Channel {

	private final DztHeader header;

	private final MinMaxAvg sampleAverage;

	private final List<Trace> traces;

	DztChannel(int index, DztHeader header, int numTraces) {
		super(index, formatName(index, header));
		this.header = header;
		this.traces = new ArrayList<>(numTraces);
		this.sampleAverage = new MinMaxAvg();
	}

	public DztHeader getHeader() {
		return header;
	}

	public void addTrace(Trace trace) {
		for (float sample : trace.getFileSamples()) {
			sampleAverage.put(sample);
		}
		traces.add(trace);
	}

	public DztChannel copy() {
		DztChannel copy = new DztChannel(getIndex(), header, traces.size());
		for (Trace trace : traces) {
			copy.traces.add(trace.copy());
		}
		copy.sampleAverage.copyFrom(sampleAverage);
		return copy;
	}

	@Override
	public List<Trace> getTraces() {
		return traces;
	}

	public void normalize() {
		float average = (float) sampleAverage.getAverage();
		for (Trace trace : traces) {
			for (int i = 0; i < trace.numSamples(); i++) {
				trace.setSample(i, trace.getSample(i) - average);
			}
		}
	}

	public void denormalize() {
		float average = (float) sampleAverage.getAverage();
		for (Trace trace : traces) {
			for (int i = 0; i < trace.numSamples(); i++) {
				trace.setSample(i, trace.getSample(i) + average);
			}
		}
	}

	private static String formatName(int index, DztHeader header) {
		String antName = header.rh_antname != null && !header.rh_antname.isBlank()
				? header.rh_antname.trim()
				: "Unknown antenna";
		return String.format("Channel %d: %s — %.0f ns / %.1f m",
				index + 1, antName, header.rhf_range, header.rhf_depth);
	}
}
