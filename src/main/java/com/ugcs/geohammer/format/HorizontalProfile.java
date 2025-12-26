package com.ugcs.geohammer.format;

import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.IndexRange;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.Arrays;

public class HorizontalProfile {

	@Nullable
	private MetaFile metaFile;

	private Color color = Color.red;

	// horizontal offset applied to profile
	private int offset = 0;

	// depths in samples
	private int[] depths;

	public HorizontalProfile(int[] depths, MetaFile metaFile) {
		Check.notNull(depths);

		this.depths = depths;
		this.metaFile = metaFile;
	}

	public HorizontalProfile(HorizontalProfile source, MetaFile metaFile) {
		Check.notNull(source);

		this.depths = Arrays.copyOf(source.depths, source.depths.length);
		this.offset = source.offset;
		this.metaFile = metaFile;
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
		return metaFile != null
				? metaFile.numValues()
				: depths.length;
	}

	private int getIndex(int localIndex, int offset) {
		// global trace index
		int globalIndex = metaFile != null
				? metaFile.getTraceIndex(localIndex)
				: localIndex;
		// offset index
		return globalIndex - offset;
	}

	private int relativeToSampleRange(int depth) {
		if (metaFile == null) {
			return depth;
		}
		IndexRange sampleRange = metaFile.getSampleRange();
		if (sampleRange == null) {
			return depth;
		}
		depth = Math.clamp(depth, sampleRange.from(), sampleRange.to() - 1);
		return depth - sampleRange.from();
	}

	public int getDepth(int index) {
		int i = getIndex(index, offset);
		int depth = i >= 0 && i < depths.length ? depths[i] : 0;
		return relativeToSampleRange(depth);
	}

	public int getLevel() {
		int n = size();
		int minDepth = n > 0 ? getDepth(0) : 0;
		int maxDepth = minDepth;
		for (int i = 0; i < n; i++) {
			int depth = getDepth(i);
			minDepth = Math.min(depth, minDepth);
			maxDepth = Math.max(depth, maxDepth);
		}
		return (minDepth + maxDepth) / 2;
	}
}
