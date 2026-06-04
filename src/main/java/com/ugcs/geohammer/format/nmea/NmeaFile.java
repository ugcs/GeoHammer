package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.SgyFileWithMeta;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.format.meta.TraceLine;
import com.ugcs.geohammer.format.meta.TraceMeta;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.SpeedUnit;
import net.sf.marineapi.nmea.sentence.DBTSentence;
import net.sf.marineapi.nmea.sentence.DPTSentence;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.MTWSentence;
import net.sf.marineapi.nmea.sentence.RMCSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.XDRSentence;
import net.sf.marineapi.nmea.sentence.ZDASentence;
import net.sf.marineapi.nmea.util.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class NmeaFile extends SgyFileWithMeta {

    private static final Logger log = LoggerFactory.getLogger(NmeaFile.class);

    private final NmeaParser nmeaParser = new NmeaParser();

    // sentences grouped by position updates (each group starts at RMC)
    private List<SentenceGroup> sentenceGroups = List.of();

    @Override
    public int numTraces() {
        return getGeoData().size();
    }

    protected void loadMeta() throws IOException {
        File source = getFile();
        Check.notNull(source);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile = new MetaFile(NmeaSchema.createSchema());
        if (Files.exists(metaPath)) {
            // load existing meta
            metaFile.load(metaPath);
        } else {
            initMeta();
        }

        syncMeta();
    }

    private void initMeta() {
        if (metaFile == null) {
            return;
        }

        TraceMeta meta = new TraceMeta();

        // lines
        TraceLine line = new TraceLine();
        line.setLineIndex(0);
        line.setFrom(0);
        line.setTo(sentenceGroups.size());
        meta.setLines(List.of(line));

        metaFile.setMetaToState(meta);
    }

    @Override
    public void saveMeta() throws IOException {
        Check.notNull(metaFile);

        File source = getFile();
        Check.notNull(source);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile.save(metaPath);
    }

    @Override
    public void syncMeta() {
        if (metaFile == null) {
            return;
        }

        for (TraceGeoData value : Nulls.toEmpty(metaFile.getValues())) {
            // clear values, but keep line index
            Integer line = value.getLine();
            value.clearValues();
            value.setLine(line);

            int traceIndex = value.getTraceIndex();
            if (traceIndex >= 0 && traceIndex < sentenceGroups.size()) {
                SentenceGroup sentenceGroup = sentenceGroups.get(traceIndex);
                for (Sentence sentence : Nulls.toEmpty(sentenceGroup.sentences())) {
                    parseValues(value, sentence);
                }
            }
        }
    }

    private void setTime(GeoData geoData, Instant time) {
        if (time == null) {
            return;
        }
        geoData.setDateTime(LocalDateTime.ofInstant(time, ZoneOffset.UTC));
    }

    private void setPosition(GeoData geoData, LatLon position) {
        if (position == null) {
            return;
        }
        geoData.setLatLon(position);
    }

    private void setValue(GeoData geoData, String header, String unit, Object value) {
        if (value == null) {
            return;
        }
        Column column = NmeaSchema.createColumn(header, unit);
        GeoData.addColumn(getGeoData(), column);
        geoData.setValue(column.getHeader(), value);
    }

    private void parseValues(GeoData geoData, Sentence sentence) {
        switch (sentence) {
            case RMCSentence rmc -> {
                setTime(geoData, nmeaParser.parseTime(rmc.getDate(), rmc.getTime()));
                setPosition(geoData, nmeaParser.parseLocation(rmc.getPosition()));
                setValue(geoData,
                        NmeaSchema.composeHeader(rmc, "Speed"),
                        "M",
                        SpeedUnit.KNOTS.toMetersPerSecond(rmc.getSpeed()));
                setValue(geoData,
                        NmeaSchema.composeHeader(rmc, "Course"),
                        "D",
                        rmc.getCourse());
            }
            case GGASentence gga -> {
                setPosition(geoData, nmeaParser.parseLocation(gga.getPosition()));
                setValue(geoData,
                        NmeaSchema.composeHeader(gga, "Altitude"),
                        String.valueOf(gga.getAltitudeUnits().toChar()),
                        gga.getAltitude());
            }
            case ZDASentence zda -> {
                setTime(geoData, nmeaParser.parseTime(zda.getDate(), zda.getTime()));
            }
            case DBTSentence dbt -> {
                setValue(geoData,
                        NmeaSchema.composeHeader(dbt, "Depth"),
                        "M",
                        dbt.getDepth());
            }
            case DPTSentence dpt -> {
                setValue(geoData,
                        NmeaSchema.composeHeader(dpt, "Depth"),
                        "M",
                        dpt.getDepth());
                setValue(geoData,
                        NmeaSchema.composeHeader(dpt, "Offset"),
                        "M",
                        dpt.getOffset());
            }
            case MTWSentence mtw -> {
                setValue(geoData,
                        NmeaSchema.composeHeader(mtw, "Temperature"),
                        "C",
                        mtw.getTemperature());
            }
            case XDRSentence xdr -> {
                for (Measurement measurement : Nulls.toEmpty(xdr.getMeasurements())) {
                    Double value = nmeaParser.parseValue(measurement);
                    if (value == null || Double.isNaN(value)) {
                        continue;
                    }
                    setValue(geoData,
                            NmeaSchema.composeHeader(xdr, measurement.getName()),
                            measurement.getUnits(),
                            value);
                }
            }
            case null, default -> {}
        }
    }

    @Override
    public void open(File file) throws IOException {
        Check.notNull(file);

        setFile(file);
        setUnsaved(false);

        sentenceGroups = readSentences(file);
        loadMeta();
    }

    private List<SentenceGroup> readSentences(File file) throws IOException {
        ArrayList<SentenceGroup> sentenceGroups = new ArrayList<>();
        ArrayList<Sentence> sentences = new ArrayList<>();
        try (NmeaReader r = new NmeaReader(file)) {
            Sentence sentence;
            while ((sentence = r.readSentence()) != null) {
                // start of group
                if (sentence instanceof RMCSentence) {
                    if (!sentences.isEmpty()) {
                        sentences.trimToSize();
                        sentenceGroups.add(new SentenceGroup(sentences));
                    }
                    sentences = new ArrayList<>();
                }
                sentences.add(sentence);
            }
        }
        if (!sentences.isEmpty()) {
            sentences.trimToSize();
            sentenceGroups.add(new SentenceGroup(sentences));
        }
        sentenceGroups.trimToSize();
        return sentenceGroups;
    }

    @Override
    public void save(File file) throws IOException {
        save(file, new IndexRange(0, numTraces()));
    }

    @Override
    public void save(File file, IndexRange range) throws IOException {
        if (metaFile == null) {
            log.warn("Cannot save range: metaFile is null");
            return;
        }

        // writeIndices does not need to be deduplicated
        List<Integer> writeIndices = new ArrayList<>();
        List<TraceGeoData> values = metaFile.getValues();
        for (int i = range.from(); i < range.to(); i++) {
            writeIndices.add(values.get(i).getTraceIndex());
        }

        try (NmeaWriter w = new NmeaWriter(file)) {
            for (int i : writeIndices) {
                SentenceGroup sentenceGroup = sentenceGroups.get(i);
                for (Sentence sentence : Nulls.toEmpty(sentenceGroup.sentences())) {
                    w.writeSentence(sentence);
                }
            }
        }
    }

    @Override
    public SgyFile copy() {
        NmeaFile copy = new NmeaFile();
        copy.setFile(getFile());
        copy.setUnsaved(isUnsaved());

        if (metaFile != null) {
            copy.metaFile = new MetaFile(ColumnSchema.copy(metaFile.getSchema()));
            copy.metaFile.setMetaToState(metaFile.getMetaFromState());
        }

        copy.sentenceGroups = sentenceGroups;
        return copy;
    }
}
