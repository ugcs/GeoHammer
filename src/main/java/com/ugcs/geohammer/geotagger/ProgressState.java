package com.ugcs.geohammer.geotagger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ProgressState {
	private final AtomicInteger processedRows = new AtomicInteger(0);
	private final int totalRows;
	private final Consumer<Integer> callback;

	public ProgressState(int totalRows, Consumer<Integer> callback) {
		this.totalRows = totalRows;
		this.callback = callback;
	}

	public void incrementProcessed() {
		updateProgress();
	}

	private void updateProgress() {
		if (callback != null && totalRows > 0) {
			int percent = (int) (processedRows.get() * 100L / totalRows);
			callback.accept(percent);
		}
	}
}