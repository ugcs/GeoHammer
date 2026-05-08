package com.ugcs.geohammer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.ugcs.geohammer.format.csv.parser.IncorrectFormatException;

class TextTest {

    @Test
    void parseTimeAcceptsThreeFractionDigits() {
        LocalTime time = Text.parseTime("110755.522", "HHmmss.fff");
        assertEquals(LocalTime.of(11, 7, 55, 522_000_000), time);
    }

    @Test
    void parseTimeAcceptsFourFractionDigits() {
        LocalTime time = Text.parseTime("110755.5220", "HHmmss.fff");
        assertEquals(LocalTime.of(11, 7, 55, 522_000_000), time);
    }

    @Test
    void parseTimeAcceptsSingleFractionDigit() {
        LocalTime time = Text.parseTime("110755.5", "HHmmss.fff");
        assertEquals(LocalTime.of(11, 7, 55, 500_000_000), time);
    }

    @Test
    void parseTimePreservesNonFractionDigits() {
        LocalTime time = Text.parseTime("110755.123456789", "HHmmss.fff");
        assertEquals(LocalTime.of(11, 7, 55, 123_456_789), time);
    }

    @Test
    void parseTimeRequiresDot() {
        assertThrows(IncorrectFormatException.class,
                () -> Text.parseTime("110755", "HHmmss.fff"));
    }

    @Test
    void parseTimeRejectsGarbage() {
        assertThrows(IncorrectFormatException.class,
                () -> Text.parseTime("not-a-time", "HHmmss.fff"));
    }

    @Test
    void parseTimeWithoutFractionPattern() {
        LocalTime time = Text.parseTime("110755", "HHmmss");
        assertEquals(LocalTime.of(11, 7, 55), time);
    }

    @Test
    void parseTimeReturnsNullOnBlank() {
        assertNull(Text.parseTime("", "HHmmss.fff"));
        assertNull(Text.parseTime("   ", "HHmmss.fff"));
    }

    @Test
    void parseDateTimeAcceptsThreeFractionDigits() {
        LocalDateTime dt = Text.parseDateTime(
                "2024-08-15 11:07:55.522", "yyyy-MM-dd HH:mm:ss.fff");
        assertEquals(LocalDateTime.of(2024, 8, 15, 11, 7, 55, 522_000_000), dt);
    }

    @Test
    void parseDateTimeAcceptsFourFractionDigits() {
        LocalDateTime dt = Text.parseDateTime(
                "2024-08-15 11:07:55.5220", "yyyy-MM-dd HH:mm:ss.fff");
        assertEquals(LocalDateTime.of(2024, 8, 15, 11, 7, 55, 522_000_000), dt);
    }

    @Test
    void parseDateTimeWithoutFractionPattern() {
        LocalDateTime dt = Text.parseDateTime(
                "2024-08-15 11:07:55", "yyyy-MM-dd HH:mm:ss");
        assertEquals(LocalDateTime.of(2024, 8, 15, 11, 7, 55), dt);
    }

    @Test
    void parseDateTimeWithGpstFormatAtEpoch() {
        LocalDateTime dt = Text.parseDateTime("0 0", Text.GPST_FORMAT);
        assertEquals(LocalDateTime.of(1980, 1, 6, 0, 0, 0), dt);
    }

    @Test
    void parseDateTimeWithGpstFormatWeeksAndSeconds() {
        // 1 week + 86400 seconds (1 day) past GPS epoch
        LocalDateTime dt = Text.parseDateTime("1 86400", Text.GPST_FORMAT);
        assertEquals(LocalDateTime.of(1980, 1, 14, 0, 0, 0), dt);
    }

    @Test
    void parseDateTimeWithGpstFormatReturnsNullOnMalformedInput() {
        assertNull(Text.parseDateTime("only-one-token", Text.GPST_FORMAT));
        assertNull(Text.parseDateTime("not-a-number 0", Text.GPST_FORMAT));
    }
}
