package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.LatLon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class NmeaFileTest {

    private static final double LAT_LON_TOLERANCE = 1e-9;

    private static final String GPS_QUALITY = "GPGGA:GPS Quality";

    @TempDir
    Path directory;

    @Test
    void open_sentencesWithMissingData_keepsRowsAndValues() throws IOException {
        File source = createFile(
                // rows before the first fix
                "$GPRMC,120000.00,V,,,,,,,281026,,,N",
                "$GPGGA,120000.00,,,,,0,00,99.99,,,,,,",
                // valid fix
                "$GPRMC,120001.00,A,5657.0000,N,02406.0000,E,1.5,90.0,281026,,,A",
                "$GPGGA,120001.00,5657.0000,N,02406.0000,E,1,08,0.9,12.5,M,20.0,M,,",
                // position only in GGA
                "$GPRMC,120002.00,V,,,,,,,281026,,,N",
                "$GPGGA,120002.00,5657.0060,N,02406.0000,E,1,08,0.9,12.5,M,20.0,M,,");

        NmeaFile file = new NmeaFile();
        file.open(source);

        List<GeoData> rows = file.getGeoData();
        assertEquals(3, rows.size());

        GeoData noFix = rows.get(0);
        assertNull(noFix.getLatLon());
        assertEquals(LocalDateTime.parse("2026-10-28T12:00:00"), noFix.getDateTime());
        assertEquals(0, noFix.getNumber(GPS_QUALITY).intValue());

        GeoData valid = rows.get(1);
        assertLatLon(56.95, 24.1, valid.getLatLon());
        assertEquals(LocalDateTime.parse("2026-10-28T12:00:01"), valid.getDateTime());
        assertEquals(1, valid.getNumber(GPS_QUALITY).intValue());

        GeoData ggaOnly = rows.get(2);
        assertLatLon(56.9501, 24.1, ggaOnly.getLatLon());
        assertEquals(LocalDateTime.parse("2026-10-28T12:00:02"), ggaOnly.getDateTime());
    }

    private File createFile(String... lines) throws IOException {
        Path path = directory.resolve("log.nmea");
        Files.write(path, List.of(lines));
        return path.toFile();
    }

    private static void assertLatLon(double latitude, double longitude, LatLon latLon) {
        assertNotNull(latLon);
        assertEquals(latitude, latLon.getLatDgr(), LAT_LON_TOLERANCE);
        assertEquals(longitude, latLon.getLonDgr(), LAT_LON_TOLERANCE);
    }
}
