package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;

public class FileUpdatedEvent extends BaseEvent {
	private final SgyFile file;

	public FileUpdatedEvent(Object source, SgyFile sgyFile) {
		super(source);
		this.file = sgyFile;
	}

	public SgyFile getFile() {
		return file;
	}
}
