package com.ugcs.geohammer.format;

import java.io.IOException;
import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;

public abstract class Channel {

	private final int index;
	private final String name;

	protected Channel(int index, String name) {
		this.index = index;
		this.name = name;
	}

	public int getIndex() {
		return index;
	}

	public String getName() {
		return name;
	}

	public abstract List<Trace> getTraces() throws IOException;
}
