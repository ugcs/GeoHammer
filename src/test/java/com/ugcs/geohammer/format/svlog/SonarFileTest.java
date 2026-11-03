package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.meta.Meta;
import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.model.LatLon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SonarFileTest {

    private static final int NUM_PACKETS = 10;

    // packets of the first line, removed in meta
    private static final int REMOVED_PACKETS = 3;

    private static final double LATITUDE = 55.5;

    private static final double LONGITUDE = 37.5;

    private static final double POSITION_STEP = 0.0001;

    private static final double POSITION_SHIFT = 0.001;

    // positions are stored in micro-minutes
    private static final double POSITION_TOLERANCE = 1e-6;

    @TempDir
    Path directory;

    @Test
    void writePositionsToSource_firstLineRemoved_keepsEveryPacket() throws IOException {
        File source = writeSvlog();
        saveMetaWithoutFirstLine(source);

        // geotag as the geotagger does for a file that is not open
        SonarFile file = new SonarFile();
        file.open(source);
        List<LatLon> positions = shiftPositions(file);
        file.copyPositionsFromMeta();
        file.writePositionsToSource();

        // without meta every packet of the source is visible
        File copy = directory.resolve("copy.svlog").toFile();
        Files.copy(source.toPath(), copy.toPath());
        SonarFile all = new SonarFile();
        all.open(copy);
        assertEquals(NUM_PACKETS, all.numTraces());
        for (int i = 0; i < NUM_PACKETS; i++) {
            LatLon expected = i < REMOVED_PACKETS
                    ? new LatLon(LATITUDE + i * POSITION_STEP, LONGITUDE + i * POSITION_STEP)
                    : positions.get(i - REMOVED_PACKETS);
            assertPosition(expected, all.getGeoData().get(i), "packet " + i);
        }

        SonarFile saved = new SonarFile();
        saved.open(source);
        Meta meta = saved.getMeta();
        assertNotNull(meta);
        assertEquals(NUM_PACKETS - REMOVED_PACKETS, meta.numValues());
        for (int i = 0; i < meta.numValues(); i++) {
            assertEquals(REMOVED_PACKETS + i, meta.getValues().get(i).getTraceIndex());
        }
    }

    private File writeSvlog() throws IOException {
        File file = directory.resolve("sonar.svlog").toFile();
        try (SvlogWriter writer = new SvlogWriter(file)) {
            for (int i = 0; i < NUM_PACKETS; i++) {
                String nmea = createRmc(i, LATITUDE + i * POSITION_STEP, LONGITUDE + i * POSITION_STEP);
                writer.writePacket(new SvlogPacket(SvlogPacketId.NMEA_WRAPPER,
                        nmea.getBytes(StandardCharsets.US_ASCII)));
            }
        }
        return file;
    }

    private static String createRmc(int second, double latitude, double longitude) {
        String body = String.format(Locale.ROOT, "GPRMC,1200%02d.00,A,%s,N,%s,E,0.0,0.0,290720,,,A",
                second, formatAngle(latitude, 2), formatAngle(longitude, 3));
        int checksum = 0;
        for (int i = 0; i < body.length(); i++) {
            checksum ^= body.charAt(i);
        }
        return String.format(Locale.ROOT, "$%s*%02X\r\n", body, checksum);
    }

    private static String formatAngle(double degrees, int degreeDigits) {
        int wholeDegrees = (int)degrees;
        double minutes = (degrees - wholeDegrees) * 60;
        return String.format(Locale.ROOT, "%0" + degreeDigits + "d%09.6f", wholeDegrees, minutes);
    }

    private static void saveMetaWithoutFirstLine(File source) throws IOException {
        SonarFile file = new SonarFile();
        file.open(source);
        Meta meta = file.getMeta();
        assertNotNull(meta);
        assertEquals(NUM_PACKETS, meta.numValues());

        List<TraceGeoData> values = meta.getValues();
        for (int i = 0; i < values.size(); i++) {
            values.get(i).setLine(i < REMOVED_PACKETS ? 0 : 1);
        }
        meta.setValues(new ArrayList<>(values.subList(REMOVED_PACKETS, values.size())));
        file.saveMeta();
    }

    private static List<LatLon> shiftPositions(SonarFile file) {
        List<LatLon> positions = new ArrayList<>();
        for (GeoData value : file.getGeoData()) {
            LatLon latLon = new LatLon(value.getLatitude() + POSITION_SHIFT, value.getLongitude() + POSITION_SHIFT);
            value.setLatLon(latLon);
            positions.add(latLon);
        }
        return positions;
    }

    private static void assertPosition(LatLon expected, GeoData value, String message) {
        LatLon latLon = value.getLatLon();
        assertNotNull(latLon, message);
        assertEquals(expected.getLatDgr(), latLon.getLatDgr(), POSITION_TOLERANCE, message);
        assertEquals(expected.getLonDgr(), latLon.getLonDgr(), POSITION_TOLERANCE, message);
    }
}
