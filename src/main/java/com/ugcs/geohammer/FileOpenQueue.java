package com.ugcs.geohammer;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public final class FileOpenQueue {

	private static final Logger log = LoggerFactory.getLogger(FileOpenQueue.class);

	private static final List<File> pendingFiles = new ArrayList<>();

	private static @Nullable Consumer<List<File>> openFileHandler;

	private FileOpenQueue() {
	}

	public static synchronized void submit(List<File> files) {
		List<File> requested = List.copyOf(Nulls.toEmpty(files));
		log.info("Open requested for {} file(s)", requested.size());
		if (openFileHandler == null) {
			pendingFiles.addAll(requested);
			return;
		}
		Consumer<List<File>> handler = openFileHandler;
		Platform.runLater(() -> handler.accept(requested));
	}

	public static synchronized void setHandler(Consumer<List<File>> handler) {
		Check.notNull(handler);

		openFileHandler = handler;
		if (pendingFiles.isEmpty()) {
			return;
		}
		List<File> files = List.copyOf(pendingFiles);
		pendingFiles.clear();
		Platform.runLater(() -> handler.accept(files));
	}
}
