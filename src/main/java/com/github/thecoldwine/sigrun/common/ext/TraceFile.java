package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.commands.DistCalculator;
import com.ugcs.gprvisualizer.app.commands.DistancesSmoother;
import com.ugcs.gprvisualizer.app.commands.EdgeFinder;
import com.ugcs.gprvisualizer.app.commands.SpreadCoordinates;
import com.ugcs.gprvisualizer.app.meta.SampleRange;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.utils.AuxElements;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Traces;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class TraceFile extends SgyFile {

    private List<Trace> traces = new ArrayList<>();

    private MetaFile metaFile;

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

        metaFile.initLocations(traces);
        metaFile.initSampleRanges(traces);
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

    @Override
    public abstract TraceFile copy();

    public abstract TraceFile copy(Range range);

    public abstract TraceFile copyHeader();

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

    public void setTraces(List<Trace> traces) {
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
        new DistCalculator().execute(this, null);
        setSpreadCoordinatesNecessary(SpreadCoordinates.isSpreadingNecessary(this));
        //smoothDistances();
        new DistancesSmoother().execute(this, null);
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
            if (metaFile == null) {
                return traces.get(index);
            }

            List<? extends GeoData> values = metaFile.getValues();
            if (values.get(index) instanceof TraceGeoData value) {
                return traces.get(value.getTraceIndex());
            }
            throw new IllegalStateException();
        }

        @Override
        public int size() {
            if (metaFile == null) {
                return traces.size();
            }

            List<? extends GeoData> values = metaFile.getValues();
            return values.size();
        }
    }
}
