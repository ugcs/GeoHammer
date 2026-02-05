package com.ugcs.geohammer.format;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.format.meta.TraceLine;
import com.ugcs.geohammer.format.meta.TraceMark;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.service.gpr.DistanceCalculator;
import com.ugcs.geohammer.service.gpr.DistanceSmoother;
import com.ugcs.geohammer.service.gpr.EdgeFinder;
import com.ugcs.geohammer.service.gpr.SpreadCoordinates;
import com.ugcs.geohammer.format.meta.TraceMeta;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.ScanProfile;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;

public abstract class TraceFile extends SgyFileWithMeta {

    protected static double SPEED_SM_NS_VACUUM = 30.0;

    protected static double SPEED_SM_NS_SOIL = SPEED_SM_NS_VACUUM / 3.0;

    private static final Logger log = LoggerFactory.getLogger(TraceFile.class);

    protected List<Trace> traces = new ArrayList<>();

    @Nullable
    private PositionFile positionFile;

    private boolean spreadCoordinatesNecessary = false;

    @Nullable
    protected HorizontalProfile groundProfile;

    @Nullable
    // amplitude
    private ScanProfile amplScan;

    protected void loadMeta(List<Trace> traces) throws IOException {
        File source = getFile();
        Check.notNull(source);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile = new MetaFile();
        if (Files.exists(metaPath)) {
            // load existing meta
            metaFile.load(metaPath);
        } else {
            // init meta
            TraceMeta meta = getMetaFromTraces(traces);
            metaFile.setMetaToState(meta);
        }

        syncMeta(traces);
    }

    private TraceMeta getMetaFromTraces(List<Trace> traces) {
        traces = Nulls.toEmpty(traces);

        TraceMeta meta = new TraceMeta();

        // sample range
        IndexRange maxSampleRange = Traces.maxSampleRange(traces);
        meta.setSampleRange(maxSampleRange);

        // lines
        TraceLine line = new TraceLine();
        line.setLineIndex(0);
        line.setFrom(0);
        line.setTo(traces.size());
        meta.setLines(List.of(line));

        // marks
        List<TraceMark> traceMarks = new ArrayList<>();
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            if (trace.isMarked()) {
                TraceMark traceMark = new TraceMark();
                traceMark.setTraceIndex(i);
                traceMarks.add(traceMark);
            }
        }
        meta.setMarks(traceMarks);

        return meta;
    }

    @Override
    public void syncMeta() {
        syncMeta(traces);
    }

    protected void syncMeta(List<Trace> traces) {
        if (metaFile == null) {
            return;
        }

        // copy location from traces to meta geodata
        for (TraceGeoData value : metaFile.getValues()) {
            int traceIndex = value.getTraceIndex();
            Trace trace = traces.get(traceIndex);

            LatLon latLon = trace.getLatLon();
            if (latLon != null) {
                value.setLatLon(trace.getLatLon());
            }

			Instant dateTime = trace.getDateTime();
			if (dateTime != null) {
				value.setDateTime(LocalDateTime.ofInstant(dateTime, ZoneOffset.UTC));
			}

        }

        // init sample ranges
        for (Trace trace : Nulls.toEmpty(traces)) {
            trace.setSampleRange(metaFile.getSampleRange());
        }
    }

    @Override
    public void saveMeta() throws IOException {
        Check.notNull(metaFile);

        File source = getFile();
        Check.notNull(source);

        // update sample range
        IndexRange sampleRange = Traces.maxSampleRange(getTraces());
        metaFile.setSampleRange(sampleRange);

        // update marks
        Set<Integer> marks = AuxElements.getMarkIndices(getAuxElements());
        metaFile.setMarks(marks);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile.save(metaPath);
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

    public @Nullable ScanProfile getAmplScan() {
        return amplScan;
    }

    public void setAmplScan(@Nullable ScanProfile amplScan) {
        this.amplScan = amplScan;
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

    public FileSnapshot<TraceFile> createSnapshotWithTraces() {
        return new SnapshotWithTraces(this);
    }

    public abstract void normalize();

    public abstract void denormalize();

    public void updateEdges() {
        new EdgeFinder().execute(this, null);
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

    protected void setTraces(List<Trace> traces) {
        this.traces = traces;
        updateEdges();
    }

    public void updateTraces() {
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            trace.setIndex(i);
        }
    }

    public void updateTraceDistances() {
        //	calcDistances();
        //	prolongDistances();
        new DistanceCalculator().execute(this, null);
        setSpreadCoordinatesNecessary(SpreadCoordinates.isSpreadingNecessary(this));
        //smoothDistances();
        new DistanceSmoother().execute(this, null);
    }

    public void copyMarkedTracesToAuxElements() {
        if (metaFile != null) {
            for (int markIndex : metaFile.getMarks()) {
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

    public int getMaxSamples() {
        return getTraces().getFirst().numSamples();
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

	public void loadFrom(TraceFile other) {
		setTraces(other.getTraces());
		setGroundProfile(other.getGroundProfile());
		setUnsaved(true);

		TraceMeta meta = other.metaFile != null
				? other.metaFile.getMetaFromState()
				: null;
		if (meta == null) {
			return; // no meta
		}
		if (metaFile == null) {
			return; // no meta file
		}
		metaFile.setMetaToState(meta);
		syncMeta();
	}

    public class TraceList extends AbstractList<Trace> {

        @Override
        public Trace get(int index) {
            int traceIndex = metaFile != null
                    ? metaFile.getTraceIndex(index)
                    : index;
            return traces.get(traceIndex);
        }

        @Override
        public int size() {
            return metaFile != null
                    ? metaFile.numValues()
                    : traces.size();
        }
    }

    public static class SnapshotWithTraces extends Snapshot<TraceFile> {

        private List<Trace> traces;

        private HorizontalProfile profile;

        public SnapshotWithTraces(TraceFile file) {
            super(file);

            traces = Traces.copy(file.traces);
            profile = file.getGroundProfile();
        }

        @Override
        public void restoreFile(Model model) {
            file.setTraces(traces);
            file.setGroundProfile(profile);

            super.restoreFile(model);
        }
    }
}
