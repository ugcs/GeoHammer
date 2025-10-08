package com.ugcs.gprvisualizer.event;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;

public class FileUpdatedEvent extends BaseEvent {
	private final SgyFile sgyFile;

	public FileUpdatedEvent(Object source, SgyFile sgyFile) {
		super(source);
		this.sgyFile = sgyFile;
	}

	public SgyFile getSgyFile() {
		return sgyFile;
	}
}
