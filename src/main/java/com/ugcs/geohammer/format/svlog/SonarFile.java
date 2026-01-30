package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFileWithMeta;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.format.meta.TraceLine;
import com.ugcs.geohammer.format.meta.TraceMeta;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SonarFile extends SgyFileWithMeta {

    private static final Logger log = LoggerFactory.getLogger(SonarFile.class);

    private List<SvlogPacket> packets = List.of();

    @Override
    public int numTraces() {
        return getGeoData().size();
    }

    protected void loadMeta() throws IOException {
        File source = getFile();
        Check.notNull(source);

        Path metaPath = MetaFile.getMetaPath(source);
        metaFile = new MetaFile(SonarSchema.createSchema());
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
        LatLon firstLatLon = null;
        Instant firstTimestamp = null;
        for (SvlogPacket packet : packets) {
            if (firstLatLon == null) {
                firstLatLon = parser.parseLocation(packet);
            }
            if (firstTimestamp == null) {
                firstTimestamp = parser.parseTime(packet);
            }
            if (firstLatLon != null && firstTimestamp != null) {
                break;
            }
        }
        if (firstLatLon == null && firstTimestamp == null) {
            throw new IllegalStateException("Cannot parse positional data");
        }

        SonarState sonarState = new SonarState();
        sonarState.setLatLon(firstLatLon);
        sonarState.setTimestamp(firstTimestamp);

        int valueIndex = 0;
        for (int i = 0; i < packets.size() && valueIndex < metaFile.numValues(); i++) {
            SvlogPacket packet = packets.get(i);
            parser.parseValues(packet, sonarState);

            TraceGeoData value = metaFile.getValues().get(valueIndex);
            if (value.getTraceIndex() == i) {
                updateGeoData(value, sonarState);
                valueIndex++;
            }
        }
    }

	private void updateGeoData(TraceGeoData value, SonarState sonarState) {
        if (sonarState.getLatLon() != null) {
            value.setLatLon(sonarState.getLatLon());
        }
        if (sonarState.getAltitude() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.ALTITUDE_COLUMN);
            value.setValue(SonarSchema.ALTITUDE_HEADER, sonarState.getAltitude());
        }
        if (sonarState.getTimestamp() != null) {
            value.setDateTime(LocalDateTime.ofInstant(sonarState.getTimestamp(), ZoneOffset.UTC));
        }
        if (sonarState.getDepth() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.DEPTH_COLUMN);
            value.setValue(SonarSchema.DEPTH_HEADER, sonarState.getDepth());
        }
        if (sonarState.getVehicleHeading() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.VEHICLE_HEADING_COLUMN);
            value.setValue(SonarSchema.VEHICLE_HEADING_HEADER, sonarState.getVehicleHeading());
        }
        if (sonarState.getTransducerHeading() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.TRANSDUCER_HEADING_COLUMN);
            value.setValue(SonarSchema.TRANSDUCER_HEADING_HEADER, sonarState.getTransducerHeading());
        }
        if (sonarState.getHeading() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.HEADING_COLUMN);
            value.setValue(SonarSchema.HEADING_HEADER, sonarState.getHeading());
        }
        if (sonarState.getPitch() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.PITCH_COLUMN);
            value.setValue(SonarSchema.PITCH_HEADER, sonarState.getPitch());
        }
        if (sonarState.getRoll() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.ROLL_COLUMN);
            value.setValue(SonarSchema.ROLL_HEADER, sonarState.getRoll());
        }
        if (sonarState.getTemperature() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.TEMPERATURE_COLUMN);
            value.setValue(SonarSchema.TEMPERATURE_HEADER, sonarState.getTemperature());
        }
        if (sonarState.getPressure() != null) {
            GeoData.addColumn(getGeoData(), SonarSchema.PRESSURE_COLUMN);
            value.setValue(SonarSchema.PRESSURE_HEADER, sonarState.getPressure());
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
            SvlogReader.ReadResult readResult;
            while ((readResult = r.readPacket()) != null) {
                if (!readResult.checksumValid()) {
                    log.warn("Invalid checksum: packet skipped [id {}]",
                            readResult.packet().getPacketId());
                    continue;
                }
                packets.add(readResult.packet());
            }
        }
        return packets;
    }

    @Override
    public void save(File file) throws IOException {
        save(file, new IndexRange(0, numTraces()));
    }

	public void save(File file, IndexRange range) throws IOException {
		if (metaFile == null) {
			log.warn("Cannot save range: metaFile is null");
			return;
		}

		Set<Integer> writeIndices = new HashSet<>();
		List<TraceGeoData> values = metaFile.getValues();
		for (int i = range.from(); i < range.to(); i++) {
			writeIndices.add(values.get(i).getTraceIndex());
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
            copy.metaFile = new MetaFile(ColumnSchema.copy(metaFile.getSchema()));
            copy.metaFile.setMetaToState(metaFile.getMetaFromState());
        }

        copy.packets = packets;
        return copy;
    }
}
