package com.ugcs.geohammer.geotagger;

import java.util.function.BiConsumer;

import com.ugcs.geohammer.util.Check;

public class Progress {

	private int progress;

	private final int maxProgress;

	private final BiConsumer<Integer, Integer> onProgressUpdate;

	public Progress(int maxProgress, BiConsumer<Integer, Integer> onProgressUpdate) {
		Check.condition(maxProgress >= 0);

		this.maxProgress = maxProgress;
		this.onProgressUpdate = onProgressUpdate;
	}

	public synchronized int getProgress() {
		return progress;
	}

	public int getMaxProgress() {
		return maxProgress;
	}

	public synchronized void increment() {
		progress = Math.min(progress + 1, maxProgress);

		if (onProgressUpdate != null) {
			onProgressUpdate.accept(progress, maxProgress);
		}
	}
}