package com.ugcs.gprvisualizer.event;

import java.io.File;
import java.util.List;

public class FileUpdatedEvent extends BaseEvent {
	private final List<File> files;

	public FileUpdatedEvent(Object source, List<File> files) {
		super(source);
		this.files = files;
	}

	public List<File> getFiles() {
		return files;
	}
}
