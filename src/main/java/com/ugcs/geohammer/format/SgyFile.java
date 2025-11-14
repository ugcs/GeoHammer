package com.ugcs.geohammer.format;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;

import com.ugcs.geohammer.model.LineSchema;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.chart.csv.axis.DistanceEstimator;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Nulls;
import org.jspecify.annotations.Nullable;

public abstract class SgyFile {

	@Nullable
	private File file;

	private boolean unsaved = true;

	private List<BaseObject> auxElements = new ArrayList<>();

	@Nullable
	private NavigableMap<Integer, IndexRange> lineRanges;

	@Nullable
	private DistanceEstimator distanceEstimator;
	
	public abstract List<GeoData> getGeoData();

	public NavigableMap<Integer, IndexRange> getLineRanges() {
		if (lineRanges == null) {
			lineRanges = LineSchema.getLineRanges(getGeoData());
		}
		return lineRanges;
	}

	public DistanceEstimator getDistanceEstimator() {
		if (distanceEstimator == null) {
			distanceEstimator = DistanceEstimator.build(this);
		}
		return distanceEstimator;
	}

	public double getDistanceAtTrace(int traceIndex) {
		List<GeoData> values = Nulls.toEmpty(getGeoData());
		if (traceIndex < 0 || traceIndex >= values.size()) {
			return Double.NaN;
		}
		GeoData value = values.get(traceIndex);
		if (value == null) {
			return Double.NaN;
		}
		DistanceEstimator distanceEstimator = getDistanceEstimator();
		return distanceEstimator.getDistanceAtTrace(traceIndex, value.getLatLon());
	}

	public void tracesChanged() {
		lineRanges = null;
		distanceEstimator = null;
	}

	public abstract int numTraces();

	public abstract void open(File file) throws IOException;
	
	public abstract void save(File file) throws IOException;

	public abstract SgyFile copy();

	public abstract FileSnapshot<? extends SgyFile> createSnapshot();

	@Nullable
	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public boolean isUnsaved() {
		return unsaved;
	}

	public void setUnsaved(boolean unsaved) {
		this.unsaved = unsaved;
	}

	public List<BaseObject> getAuxElements() {
		return auxElements;
	}

	public void setAuxElements(List<BaseObject> auxElements) {
		this.auxElements = auxElements;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(file);
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (!(other instanceof SgyFile sgyFile)) {
			return false;
		}
        return Objects.equals(file, sgyFile.file);
	}
}
