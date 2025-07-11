package com.ugcs.gprvisualizer.math;

import java.awt.Color;

public class HorizontalProfile {

	private static final int SMOOTHING_WINDOW = 7;

	private Color color = Color.red;

	// horizontal offset applied to profile
	private int offset = 0;

	// depths in samples
	private int[] depths;

	private int minDepth;

	private int maxDepth;

	private int avgDepth;

	public HorizontalProfile(int size) {
		depths = new int[size];
	}

	public HorizontalProfile(int[] depths) {
		this.depths = depths;
	}

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public int size() {
		return depths.length;
	}

	public int getDepth(int index) {
		int i = index - offset;
		return i >= 0 && i < depths.length ? depths[i] : 0;
	}

	public void setDepth(int index, int depth) {
		int i = index - offset;
		if (i >= 0 && i < depths.length) {
			depths[i] = depth;
		}
	}

	public int getMinDepth() {
		return minDepth;
	}

	public int getMaxDepth() {
		return maxDepth;
	}

	public int getAverageDepth() {
		return avgDepth;
	}

	public int getHeight() {
		return maxDepth - minDepth;
	}

	public void finish() {
		smooth();

		minDepth = depths.length > 0 ? depths[0] : 0;
		maxDepth = minDepth;

		long sum = 0;
        for (int depth : depths) {
            minDepth = Math.min(depth, minDepth);
            maxDepth = Math.max(depth, maxDepth);
            sum += depth;
        }
		avgDepth = depths.length > 0 ? (int)(sum / depths.length) : 0;
	}

	private void smooth() {
		int[] result = new int[depths.length];
		for (int i = 0; i < depths.length; i++) {
			result[i] = weightedAverageAt(i);
		}
		depths = result;
	}

	private int weightedAverageAt(int i) {
		int r = SMOOTHING_WINDOW;

		int from = i - r;
		from = Math.max(0, from);
		int to = i + r;
		to = Math.min(to, depths.length - 1);
		double sum = 0;
		double cnt = 0;

		for (int j = from; j <= to; j++) {
			double kfx = (double)(r + j - i) / (r * 2);
			double kf = kfx * kfx * (1 - kfx) * (1 - kfx);

			sum += depths[j] * kf;
			cnt += kf;
		}

		return (int) Math.round(sum / cnt);
	}
}
