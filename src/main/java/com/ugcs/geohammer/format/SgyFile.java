package com.ugcs.geohammer.format;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.ugcs.geohammer.chart.csv.axis.DistanceEstimator;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LineSchema;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import org.jspecify.annotations.Nullable;

public abstract class SgyFile {

	private static final long LOCK_TIMEOUT_SECONDS = 30;

	// shared by all files, so that a version is never reused,
	// even when an undo sets a file back to its previous version
	private static final AtomicLong lastVersion = new AtomicLong();

	private final ReentrantLock lock = new ReentrantLock();

	private volatile long version = lastVersion.incrementAndGet();

	@Nullable
	private File file;

	private boolean unsaved = true;

	private List<BaseObject> auxElements = new ArrayList<>();

	@Nullable
	private NavigableMap<Integer, IndexRange> lineRanges;

	@Nullable
	private DistanceEstimator distanceEstimator;

	public abstract List<GeoData> getGeoData();

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}

	public void updateVersion() {
		version = lastVersion.incrementAndGet();
	}

	public boolean isLocked() {
		return lock.isLocked();
	}

	public boolean isLockedByCurrentThread() {
		return lock.isHeldByCurrentThread();
	}

	private void acquireLock() {
		boolean acquired;
		try {
			acquired = lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new CancellationException();
		}
		if (!acquired) {
			throw new FileLockedException(file);
		}
	}

	private void releaseLock() {
		lock.unlock();
	}

	public <T> T withLock(Callable<T> action) throws Exception {
		Check.notNull(action);

		acquireLock();
		try {
			return action.call();
		} finally {
			releaseLock();
		}
	}

	// locks all files or none of them
	public static <T> T withLock(Collection<? extends SgyFile> files, Callable<T> action) throws Exception {
		Check.notNull(files);
		Check.notNull(action);

		List<SgyFile> locked = new ArrayList<>(files.size());
		try {
			for (SgyFile file : files) {
				file.acquireLock();
				locked.add(file);
			}
			return action.call();
		} finally {
			for (SgyFile file : locked) {
				file.releaseLock();
			}
		}
	}

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

    public abstract void save(File file, IndexRange range) throws IOException;

	public abstract SgyFile copy();

	public abstract FileSnapshot<? extends SgyFile> createSnapshot();

	@Nullable
	public File getFile() {
		return file;
	}

	public void setFile(@Nullable File file) {
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
