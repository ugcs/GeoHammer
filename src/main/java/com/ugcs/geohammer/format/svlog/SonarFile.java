package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.format.SgyFileWithMeta;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.format.meta.TraceLine;
import com.ugcs.geohammer.format.meta.TraceMeta;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Traces;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SonarFile extends SgyFileWithMeta {

    private List<SvlogPacket> packets = List.of();

    @Override
    public int numTraces() {
        return 0;
    }

    protected void loadMeta() throws IOException {
        File source = getFile();
        Check.notNull(source);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile = new MetaFile();
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

        LatLon latLon = null;
        Instant timestamp = null;
        for (SvlogPacket packet : packets) {
            if (latLon == null) {
                latLon = parser.parseLocation(packet);
            }
            if (timestamp == null) {
                timestamp = parser.parseTime(packet);
            }
            if (latLon != null && timestamp != null) {
                break;
            }
        }
        if (latLon == null && timestamp == null) {
            return;
        }

        int k = 0;
        for (int i = 0; i < packets.size() && k < metaFile.getValues().size(); i++) {
            LatLon iLatLon = parser.parseLocation(packets.get(i));
            if (iLatLon != null) {
                latLon = iLatLon;
            }
            Instant iTimestamp = parser.parseTime(packets.get(i));
            if (iTimestamp != null) {
                timestamp = iTimestamp;
            }
            TraceGeoData value = metaFile.getValues().get(k);
            if (value.getTraceIndex() == i) {
                if (latLon != null) {
                    value.setLatLon(latLon);
                }
                if (timestamp != null) {
                    value.setDateTime(LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC));
                }
                k++;
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

        try (SvlogWriter w = new SvlogWriter(file)) {
            for (TraceGeoData value : metaFile.getValues()) {
                SvlogPacket packet = packets.get(value.getTraceIndex());
                w.writePacket(packet);
            }
        }
    }

    @Override
    public SonarFile copy() {
        SonarFile copy = new SonarFile();
        copy.setFile(getFile());
        copy.setUnsaved(isUnsaved());

        if (metaFile != null) {
            copy.metaFile = new MetaFile();
            copy.metaFile.setMetaToState(metaFile.getMetaFromState());
        }

        copy.packets = packets;
        return copy;
    }
}
