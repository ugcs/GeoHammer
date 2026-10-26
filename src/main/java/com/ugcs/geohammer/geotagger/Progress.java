package com.ugcs.geohammer.geotagger;

import java.util.function.BiConsumer;

import com.ugcs.geohammer.util.Check;

public class Progress {

	private int progress;

	private final int maxProgress;

	private int lastPercent = -1;

	private final BiConsumer<Integer, Integer> onProgressUpdate;

	public Progress(int maxProgress, BiConsumer<Integer, Integer> onProgressUpdate) {
		Check.condition(maxProgress >= 0);

		this.maxProgress = maxProgress;
		this.onProgressUpdate = onProgressUpdate;
	}

	public void increment() {
		progress = Math.min(progress + 1, maxProgress);

		int percent = maxProgress > 0 ? (int) ((long) progress * 100 / maxProgress) : 0;
		if (percent == lastPercent) {
			return;
		}
		lastPercent = percent;
		if (onProgressUpdate != null) {
			onProgressUpdate.accept(progress, maxProgress);
		}
	}
}
