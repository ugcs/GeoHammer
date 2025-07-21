package com.github.thecoldwine.sigrun.common.ext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.quality.LineSchema;
import com.ugcs.gprvisualizer.app.undo.FileSnapshot;
import com.ugcs.gprvisualizer.app.undo.UndoSnapshot;
import com.ugcs.gprvisualizer.utils.Range;
import org.jspecify.annotations.Nullable;

public abstract class SgyFile {

	@Nullable
	private File file;

	private boolean unsaved = true;

	private List<BaseObject> auxElements = new ArrayList<>();

	@Nullable
	private SortedMap<Integer, Range> lineRanges;
	
	public abstract List<GeoData> getGeoData();

	public SortedMap<Integer, Range> getLineRanges() {
		if (lineRanges == null) {
			lineRanges = LineSchema.getLineRanges(getGeoData());
		}
		return lineRanges;
	}

	public void rebuildLineRanges() {
		lineRanges = null; // would be rebuilt on next access
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
