package com.ugcs.geohammer.format.nmea;

import net.sf.marineapi.nmea.sentence.Sentence;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NmeaParserTest {

    private static final String VOID_RMC = "$GPRMC,123519.00,V,,,,,,,281026,,,N";

    private static final String NO_FIX_GGA = "$GPGGA,123519.00,,,,,0,00,99.99,,,,,,";

    private static final String NO_TIME_GGA = "$GPGGA,,,,,,0,00,99.99,,,,,,";

    @Test
    void parseLocation_voidRmc_returnsNull() {
        NmeaParser parser = new NmeaParser();
        Sentence rmc = parser.parseSentence(VOID_RMC);

        assertNull(parser.parseLocation(rmc));
    }

    @Test
    void parseTime_voidRmc_returnsTime() {
        NmeaParser parser = new NmeaParser();
        Sentence rmc = parser.parseSentence(VOID_RMC);

        assertEquals(Instant.parse("2026-10-28T12:35:19Z"), parser.parseTime(rmc));
    }

    @Test
    void parseLocation_noFixGga_returnsNull() {
        NmeaParser parser = new NmeaParser();
        Sentence gga = parser.parseSentence(NO_FIX_GGA);

        assertNull(parser.parseLocation(gga));
        assertNull(parser.parseAltitude(gga));
    }

    @Test
    void parseFixQuality_noFixGga_returnsZero() {
        NmeaParser parser = new NmeaParser();
        Sentence gga = parser.parseSentence(NO_FIX_GGA);

        assertEquals(0, parser.parseFixQuality(gga));
    }

    @Test
    void parseTimeOfDay_emptyTime_returnsNull() {
        NmeaParser parser = new NmeaParser();
        Sentence gga = parser.parseSentence(NO_TIME_GGA);

        assertNull(parser.parseTimeOfDay(gga, Instant.parse("2026-10-28T12:00:00Z")));
    }
}
