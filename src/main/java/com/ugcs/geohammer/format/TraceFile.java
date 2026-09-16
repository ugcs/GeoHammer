package com.ugcs.geohammer.format;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.meta.Meta;
import com.ugcs.geohammer.format.meta.MetaFiles;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.undo.TempStore;
import com.ugcs.geohammer.model.undo.TraceCodec;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.service.gpr.BackgroundNoiseRemover;
import com.ugcs.geohammer.service.gpr.DistanceCalculator;
import com.ugcs.geohammer.service.gpr.DistanceSmoother;
import com.ugcs.geohammer.service.gpr.SpreadCoordinates;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Traces;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;

public abstract class TraceFile extends SgyFileWithMeta {

    protected static final double SPEED_SM_NS_VACUUM = 30.0;

    protected static final double SPEED_SM_NS_SOIL = SPEED_SM_NS_VACUUM / 3.0;

    private static final Logger log = LoggerFactory.getLogger(TraceFile.class);

    protected List<Trace> traces = new ArrayList<>();

    @Nullable
    private PositionFile positionFile;

    private boolean spreadCoordinatesNecessary = false;

    @Nullable
    protected HorizontalProfile groundProfile;

    @Nullable
    private volatile SampleStatistics statistics;

    @Override
    protected Meta initMeta(ColumnSchema schema) {
        Meta newMeta = Meta.ofSingleLine(schema, traces.size());
        // sample range
        newMeta.setSampleRange(Traces.maxSampleRange(traces));
        // marks
        Set<Integer> metaMarks = new HashSet<>();
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            if (trace.isMarked()) {
                metaMarks.add(i);
            }
        }
        newMeta.setMarks(metaMarks);
        return newMeta;
    }

    @Override
    public void saveMeta() throws IOException {
        Check.notNull(meta);

        File source = getFile();
        Check.notNull(source);

        // update sample range
        IndexRange sampleRange = Traces.maxSampleRange(getTraces());
        meta.setSampleRange(sampleRange);

        // update marks
        Set<Integer> marks = AuxElements.getMarkIndices(getAuxElements());
        meta.setMarks(marks);

        MetaFiles.writeMetaOf(source, meta);
    }

    @Override
    public void syncMeta() {
        if (meta == null) {
            return;
        }

        // copy location from traces to meta geodata
        for (TraceGeoData value : meta.getValues()) {
            int traceIndex = value.getTraceIndex();
            Trace trace = traces.get(traceIndex);

            LatLon latLon = trace.getLatLon();
            if (latLon != null) {
                value.setLatLon(trace.getLatLon());
            }

			Instant dateTime = trace.getDateTime();
			if (dateTime != null) {
                value.setTimestamp(dateTime.toEpochMilli());
			}
        }

        // init sample ranges
        for (Trace trace : Nulls.toEmpty(traces)) {
            trace.setSampleRange(meta.getSampleRange());
        }
    }

    public abstract int getSampleInterval();

    public abstract double getSamplesToCmGrn();

    public abstract double getSamplesToCmAir();

    public double getSamplesPerMeter() {
        // sample distance in cm
        double sampleDistance = getSamplesToCmAir();
        // num samples in a meter
        return 100.0 / sampleDistance;
    }

    public boolean isSpreadCoordinatesNecessary() {
        return spreadCoordinatesNecessary;
    }

    public void setSpreadCoordinatesNecessary(boolean spreadCoordinatesNecessary) {
        this.spreadCoordinatesNecessary = spreadCoordinatesNecessary;
    }

    public void loadPositionFile(FileTemplates templates) throws IOException {
        Optional<File> file = PositionFile.findFor(this);
        if (file.isPresent()) {
            PositionFile positionFile = new PositionFile(templates);
            positionFile.load(file.get());
            this.positionFile = positionFile;

            HorizontalProfile groundProfile = new HorizontalProfile();
            groundProfile.setAltitudes(positionFile.traceValues(
                    this, positionFile.getAltitudeHeader()));
            groundProfile.setEllipsoidalHeights(positionFile.traceValues(
                    this, positionFile.getEllipsoidalHeightHeader()));
            groundProfile.buildSurface(this);
            this.groundProfile = groundProfile;
        } else {
            log.info("No position file found for {}", this.getFile());
        }
    }

    public void setPositionFile(PositionFile positionFile) {
        this.positionFile = positionFile;
    }

    public PositionFile getPositionFile() {
        return positionFile;
    }

    public HorizontalProfile getGroundProfile() {
        return groundProfile;
    }

    public void setGroundProfile(HorizontalProfile groundProfile) {
        this.groundProfile = groundProfile;
    }

    public SampleStatistics getStatistics() {
        SampleStatistics stats = statistics;
        if (stats == null) {
            stats = SampleStatistics.compute(this);
            statistics = stats;
        }
        return stats;
    }

    @Override
    public void tracesChanged() {
        super.tracesChanged();

        statistics = null;
    }

    public static double convertDegreeFraction(double org) {
        org = org / 100.0;
        int dgr = (int) org;
        double fract = org - dgr;
        double rx = dgr + fract / 60.0 * 100.0;
        return rx;
    }

    public static double convertBackDegreeFraction(double org) {
        int dgr = (int) org;
        double fr = org - dgr;
        double fr2 = fr * 60.0 / 100.0;
        double r = 100.0 * (dgr + fr2);

        return r;
    }

    @Override
    public abstract TraceFile copy();

    public void loadFrom(TraceFile other) {
        loadFrom(other, () -> {
            setTraces(other.getTraces());
            setGroundProfile(other.getGroundProfile());
        });
    }

    public FileSnapshot<TraceFile> createSnapshotWithTraces() {
        return createSnapshotWithTraces(ByteOrder.BIG_ENDIAN);
    }

    protected FileSnapshot<TraceFile> createSnapshotWithTraces(ByteOrder byteOrder) {
        try {
            return new SnapshotWithTraces(this, byteOrder);
        } catch (IOException e) {
            log.error("Failed to create snapshot", e);
            return null;
        }
    }

    @Override
    public int numTraces() {
        return getTraces().size();
    }

    public List<Trace> getTraces() {
        return new TraceList();
    }

    public List<Trace> getFileTraces() {
        return traces;
    }

    public int getFileTraceIndex(int index) {
        return meta != null
                ? meta.getTraceIndex(index)
                : index;
    }

    protected void setTraces(List<Trace> traces) {
        this.traces = traces;
        tracesChanged();
    }

    public void updateTraces() {
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            trace.setIndex(i);
        }
    }

    public void updateTraceDistances() {
        new DistanceCalculator().execute(this, null);
        setSpreadCoordinatesNecessary(SpreadCoordinates.isSpreadingNecessary(this));
        new DistanceSmoother().execute(this, null);
    }

    public void copyMarkedTracesToAuxElements() {
        if (meta != null) {
            for (int markIndex : meta.getMarks()) {
                TraceKey traceKey = new TraceKey(this, markIndex);
                getAuxElements().add(new FoundPlace(traceKey, AppContext.model));
            }
        } else {
            // TODO GPR_LINES for compatibility with DZT
            for (Trace trace: getTraces()) {
                if (trace.isMarked()) {
                    TraceKey traceKey = new TraceKey(this, trace.getIndex());
                    getAuxElements().add(new FoundPlace(traceKey, AppContext.model));
                }
            }
        }
    }

    public int maxSamples() {
        int maxSamples = 0;
        for (Trace trace : getTraces()) {
            maxSamples = Math.max(maxSamples, trace.numSamples());
        }
        return maxSamples;
    }

    public void addLineBoundaryMarks() {
        NavigableMap<Integer, IndexRange> lineRanges = getLineRanges();
        if (lineRanges.size() <= 1) {
            return;
        }

		int i = 0;
		int size = lineRanges.size();
		for (IndexRange range : lineRanges.values()) {
            // Mark start of each line (except first)
            if (i > 0) {
                TraceKey traceKey = new TraceKey(this, range.from());
                getAuxElements().add(new FoundPlace(traceKey, AppContext.model));
            }
			i++;
		}
	}

	public void removeBackground(@Nullable UndoModel undoModel) {
		BackgroundNoiseRemover filter = new BackgroundNoiseRemover(undoModel);
		filter.execute(this, null);
		if (meta != null && getTraces().size() > 1) {
			meta.setBackgroundRemoved(true);
		}
	}

	public boolean isBackgroundRemoved() {
		return meta != null && meta.isBackgroundRemoved();
	}

    public class TraceList extends AbstractList<Trace> {

        @Override
        public Trace get(int index) {
            int traceIndex = meta != null
                    ? meta.getTraceIndex(index)
                    : index;
            return traces.get(traceIndex);
        }

        @Override
        public int size() {
            return meta != null
                    ? meta.numValues()
                    : traces.size();
        }
    }

    public static class SnapshotWithTraces extends Snapshot<TraceFile> {

        private final TempStore.Entry tracesEntry;

        private final HorizontalProfile profile;

        public SnapshotWithTraces(TraceFile file, ByteOrder byteOrder) throws IOException {
            super(file);

            TempStore tempStore = AppContext.getInstance(TempStore.class);
            tracesEntry = tempStore.newEntry();
            tracesEntry.write(out -> TraceCodec.write(out, file.traces, byteOrder));

            profile = file.getGroundProfile();
        }

        @Override
        public void restoreFile(Model model) throws IOException {
            List<Trace> traces = tracesEntry.read(TraceCodec::read);
            file.setTraces(traces);

            file.setGroundProfile(profile);

            super.restoreFile(model);
        }

        @Override
        public void discard() {
            tracesEntry.close();
        }
    }
}
