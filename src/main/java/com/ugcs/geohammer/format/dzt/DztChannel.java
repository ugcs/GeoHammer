package com.ugcs.geohammer.format.dzt;

import java.util.List;

import com.ugcs.geohammer.format.Channel;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.MinMaxAvg;

public class DztChannel extends Channel {

	private final DztHeader header;
	private final MinMaxAvg sampleAverage;
	private List<Trace> traces;

	DztChannel(int index, String name, DztHeader header,
			List<Trace> traces, MinMaxAvg sampleAverage) {
		super(index, name);
		this.header = header;
		this.traces = traces;
		this.sampleAverage = sampleAverage;
	}

	public DztHeader getHeader() {
		return header;
	}

	public MinMaxAvg getSampleAverage() {
		return sampleAverage;
	}

	@Override
	public List<Trace> getTraces() {
		return traces;
	}

	void setTraces(List<Trace> traces) {
		this.traces = traces;
	}

	static String formatName(int index, DztHeader header) {
		String antName = header.rh_antname != null && !header.rh_antname.isBlank()
				? header.rh_antname.trim()
				: "Unknown antenna";
		return String.format("Channel %d: %s — %.0f ns / %.1f m",
				index + 1, antName, header.rhf_range, header.rhf_depth);
	}
}
