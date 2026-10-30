package com.ugcs.geohammer.format.svlog;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SvlogParserTest {

    @Test
    void parseTime_voidRmc_returnsTime() {
        SvlogParser parser = new SvlogParser();
        SvlogPacket packet = createNmeaPacket("$GPRMC,123519.00,V,,,,,,,281026,,,N");

        assertNull(parser.parseLocation(packet));
        assertEquals(Instant.parse("2026-10-28T12:35:19Z"), parser.parseTime(packet));
    }

    @Test
    void parseFixQuality_noFixGga_returnsZero() {
        SvlogParser parser = new SvlogParser();
        SvlogPacket packet = createNmeaPacket("$GPGGA,123519.00,,,,,0,00,99.99,,,,,,");

        assertNull(parser.parseLocation(packet));
        assertNull(parser.parseAltitude(packet));
        assertEquals(0, parser.parseFixQuality(packet));
    }

    private static SvlogPacket createNmeaPacket(String nmea) {
        return new SvlogPacket(SvlogPacketId.NMEA_WRAPPER, nmea.getBytes(StandardCharsets.US_ASCII));
    }
}
