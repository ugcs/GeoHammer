package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.format.SgyFileWithMeta;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.format.meta.TraceLine;
import com.ugcs.geohammer.format.meta.TraceMeta;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SonarFile extends SgyFileWithMeta {

    private static final String DEPTH_HEADER = "Depth";

    private static final ColumnSchema COLUMN_SCHEMA = createSonarColumnSchema();

    private List<SvlogPacket> packets = List.of();

    @Override
    public int numTraces() {
        return getGeoData().size();
    }

    private static ColumnSchema createSonarColumnSchema() {
        ColumnSchema schema = ColumnSchema.copy(TraceGeoData.SCHEMA);
        schema.addColumn(new Column(DEPTH_HEADER)
                .withUnit("m")
                .withDisplay(true)
                .withReadOnly(true));
        return schema;
    }

    protected void loadMeta() throws IOException {
        File source = getFile();
        Check.notNull(source);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile = new MetaFile(COLUMN_SCHEMA);
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
        line.setTo(packets.size());
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

        SvlogParser parser = new SvlogParser();

        // first location and time in a stream
        LatLon lastLatLon = null;
        Instant lastTimestamp = null;
        for (SvlogPacket packet : packets) {
            if (lastLatLon == null) {
                lastLatLon = parser.parseLocation(packet);
            }
            if (lastTimestamp == null) {
                lastTimestamp = parser.parseTime(packet);
            }
            if (lastLatLon != null && lastTimestamp != null) {
                break;
            }
        }
        if (lastLatLon == null && lastTimestamp == null) {
            return;
        }

        Double lastDepth = null;
        int valueIndex = 0;
        for (int i = 0; i < packets.size() && valueIndex < metaFile.numValues(); i++) {
            SvlogPacket packet = packets.get(i);

            LatLon latLon = parser.parseLocation(packet);
            if (latLon != null) {
                lastLatLon = latLon;
            }
            Instant timestamp = parser.parseTime(packet);
            if (timestamp != null) {
                lastTimestamp = timestamp;
            }
            Double depth = parser.parseDepth(packet);
            if (depth != null) {
                lastDepth = depth;
            }

            TraceGeoData value = metaFile.getValues().get(valueIndex);
            if (value.getTraceIndex() == i) {
                if (lastLatLon != null) {
                    value.setLatLon(lastLatLon);
                }
                if (lastTimestamp != null) {
                    value.setDateTime(LocalDateTime.ofInstant(lastTimestamp, ZoneOffset.UTC));
                }
                if (lastDepth != null) {
                    value.setValue(DEPTH_HEADER, lastDepth);
                }
                valueIndex++;
            }
        }
    }

    @Override
    public void open(File file) throws IOException {
        Check.notNull(file);

        setFile(file);
        setUnsaved(false);

        packets = readPackets(file);
        loadMeta();
    }

    private List<SvlogPacket> readPackets(File file) throws IOException {
        List<SvlogPacket> packets = new ArrayList<>();
        try (SvlogReader r = new SvlogReader(file)) {
            SvlogPacket packet;
            while ((packet = r.readPacket()) != null) {
                packets.add(packet);
            }
        }
        return packets;
    }

    @Override
    public void save(File file) throws IOException {
        if (metaFile == null) {
            return;
        }

        Set<Integer> writeIndices = new HashSet<>();
        for (TraceGeoData value : metaFile.getValues()) {
            writeIndices.add(value.getTraceIndex());
        }

        SvlogParser parser = new SvlogParser();
        try (SvlogWriter w = new SvlogWriter(file)) {
            for (int i = 0; i < packets.size(); i++) {
                SvlogPacket packet = packets.get(i);
                if (writeIndices.contains(i) || parser.isMetaPacket(packet)) {
                    w.writePacket(packet);
                }
            }
        }
    }

    @Override
    public SonarFile copy() {
        SonarFile copy = new SonarFile();
        copy.setFile(getFile());
        copy.setUnsaved(isUnsaved());

        if (metaFile != null) {
            copy.metaFile = new MetaFile(COLUMN_SCHEMA);
            copy.metaFile.setMetaToState(metaFile.getMetaFromState());
        }

        copy.packets = packets;
        return copy;
    }
}
