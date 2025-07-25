package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.commands.DistanceCalculator;
import com.ugcs.gprvisualizer.app.commands.DistanceSmoother;
import com.ugcs.gprvisualizer.app.commands.EdgeFinder;
import com.ugcs.gprvisualizer.app.commands.SpreadCoordinates;
import com.ugcs.gprvisualizer.app.meta.SampleRange;
import com.ugcs.gprvisualizer.app.meta.TraceMeta;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.undo.FileSnapshot;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.math.ScanProfile;
import com.ugcs.gprvisualizer.utils.AuxElements;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Traces;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class TraceFile extends SgyFile {

    protected static double SPEED_SM_NS_VACUUM = 30.0;

    protected static double SPEED_SM_NS_SOIL = SPEED_SM_NS_VACUUM / 3.0;

    protected List<Trace> traces = new ArrayList<>();

    @Nullable
    protected MetaFile metaFile;

    @Nullable
    private PositionFile positionFile;

    @Nullable
    private String groundProfileTraceSemantic;

    private boolean spreadCoordinatesNecessary = false;

    @Nullable
    protected HorizontalProfile groundProfile;

    @Nullable
    // amplitude
    private ScanProfile amplScan;

    public MetaFile getMetaFile() {
        return metaFile;
    }

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
            metaFile.init(traces);
        }

        metaFile.initTraces(traces);
    }

    public void saveMeta() throws IOException {
        Check.notNull(metaFile);

        File source = getFile();
        Check.notNull(source);

        // update sample range
        SampleRange sampleRange = Traces.maxSampleRange(getTraces());
        metaFile.setSampleRange(sampleRange);

        // update marks
        Set<Integer> marks = AuxElements.getMarkIndices(getAuxElements());
        metaFile.setMarks(marks);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile.save(metaPath);
    }

    public void updateTracesFromMeta() {
        if (metaFile != null) {
            metaFile.initTraces(traces);
        }
    }

    public abstract int getSampleInterval();

    public abstract double getSamplesToCmGrn();

    public abstract double getSamplesToCmAir();

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

    public void setGroundProfileSource(PositionFile positionFile) {
        this.positionFile = positionFile;
    }

    public PositionFile getGroundProfileSource() {
        return positionFile;
    }

    public @Nullable String getGroundProfileTraceSemantic() {
        return groundProfileTraceSemantic;
    }

    public void setGroundProfileTraceSemantic(@Nullable String groundProfileTraceSemantic) {
        this.groundProfileTraceSemantic = groundProfileTraceSemantic;
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

    public abstract void save(File file, Range range) throws IOException;

    @Override
    public abstract TraceFile copy();

    @Override
    public FileSnapshot<TraceFile> createSnapshot() {
        return new Snapshot(this);
    }

    public FileSnapshot<TraceFile> createSnapshotWithTraces() {
        return new SnapshotWithTraces(this);
    }

    public abstract void normalize();

    public abstract void denormalize();

    @Override
    public List<GeoData> getGeoData() {
        return metaFile != null
                ? (List<GeoData>)metaFile.getValues()
                : List.of();
    }

    @Override
    public int numTraces() {
        return getTraces().size();
    }

    public List<Trace> getTraces() {
        return new TraceList();
    }

    protected void setTraces(List<Trace> traces) {
        this.traces = traces;
        new EdgeFinder().execute(this, null);
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
                    ? metaFile.numTraces()
                    : traces.size();
        }
    }

    public static class Snapshot extends FileSnapshot<TraceFile> {

        private final TraceMeta meta;

        public Snapshot(TraceFile file) {
            super(file);

            this.meta = copyMeta(file);
        }

        private static TraceMeta copyMeta(TraceFile file) {
            MetaFile metaFile = file.getMetaFile();
            return metaFile != null
                    ? metaFile.getMetaFromState()
                    : null;
        }

        @Override
        public void restoreFile(Model model) {
            if (meta == null) {
                return; // no meta
            }
            MetaFile metaFile = file.getMetaFile();
            if (metaFile == null) {
                return; // no meta file
            }

            metaFile.setMetaToState(meta);
            file.updateTracesFromMeta();
        }
    }

    public static class SnapshotWithTraces extends Snapshot {

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
