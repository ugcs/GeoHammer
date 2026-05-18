package com.ugcs.geohammer.format.csv.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;

import org.junit.jupiter.api.Test;

import com.ugcs.geohammer.format.csv.parser.ParseWarnings.ParseWarning;

class ParseWarningsTest {

    @Test
    void emptyByDefault() {
        ParseWarnings warnings = new ParseWarnings();

        assertTrue(warnings.isEmpty());
        assertTrue(warnings.all().isEmpty());
        assertEquals("", warnings.format());
    }

    @Test
    void isNotEmptyAfterAdd() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");

        assertFalse(warnings.isEmpty());
    }

    @Test
    void addFormatErrorDedupesByColumnAndFormat() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");
        warnings.addFormatError("Timestamp", "12:34:57", "HH:mm:ss");
        warnings.addFormatError("Timestamp", "12:34:58", "HH:mm:ss");

        assertEquals(1, warnings.all().size());
        ParseWarning warning = warnings.all().iterator().next();
        assertEquals(3, warning.getCount());
    }

    @Test
    void addFormatErrorSeparatesDifferentFormats() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");
        warnings.addFormatError("Timestamp", "12.34.56", "HH.mm.ss");

        assertEquals(2, warnings.all().size());
    }

    @Test
    void addFormatErrorSeparatesDifferentColumns() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Time", "12:34:56", "HH:mm:ss");
        warnings.addFormatError("Date", "12:34:56", "HH:mm:ss");

        assertEquals(2, warnings.all().size());
    }

    @Test
    void addNumberErrorDedupesByColumn() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addNumberError("Timestamp", "abc");
        warnings.addNumberError("Timestamp", "xyz");

        assertEquals(1, warnings.all().size());
        assertEquals(2, warnings.all().iterator().next().getCount());
    }

    @Test
    void formatErrorAndNumberErrorOnSameColumnAreSeparate() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");
        warnings.addNumberError("Timestamp", "abc");

        assertEquals(2, warnings.all().size());
    }

    @Test
    void blankValueIsIgnored() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "", "HH:mm:ss");
        warnings.addFormatError("Timestamp", "   ", "HH:mm:ss");
        warnings.addNumberError("Timestamp", "");
        warnings.addNumberError("Timestamp", "   ");

        assertTrue(warnings.isEmpty());
    }

    @Test
    void nullValueIsIgnored() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", null, "HH:mm:ss");
        warnings.addNumberError("Timestamp", null);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void preservesInsertionOrder() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Time", "x", "HH:mm:ss");
        warnings.addNumberError("Timestamp", "y");
        warnings.addFormatError("Date", "z", "yyyy-MM-dd");

        Iterator<ParseWarning> it = warnings.all().iterator();
        assertEquals("Time", it.next().getColumn());
        assertEquals("Timestamp", it.next().getColumn());
        assertEquals("Date", it.next().getColumn());
    }

    @Test
    void allIsUnmodifiable() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");

        assertThrows(UnsupportedOperationException.class,
                () -> warnings.all().clear());
    }

    @Test
    void keepsFirstValueAsExample() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");
        warnings.addFormatError("Timestamp", "99:99:99", "HH:mm:ss");

        ParseWarning warning = warnings.all().iterator().next();
        assertTrue(warning.getMessage().contains("12:34:56"));
        assertFalse(warning.getMessage().contains("99:99:99"));
    }

    @Test
    void formatSingleWarning() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");

        assertEquals(
                "Timestamp: Cannot parse '12:34:56' with format 'HH:mm:ss' (occurrences: 1)",
                warnings.format());
    }

    @Test
    void formatNumberWarning() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addNumberError("Timestamp", "abc");

        assertEquals(
                "Timestamp: Cannot parse 'abc' as a number (occurrences: 1)",
                warnings.format());
    }

    @Test
    void formatMultipleWarningsJoinedByNewline() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Time", "x", "HH:mm:ss");
        warnings.addNumberError("Timestamp", "y");

        assertEquals(
                "Time: Cannot parse 'x' with format 'HH:mm:ss' (occurrences: 1)\n"
                        + "Timestamp: Cannot parse 'y' as a number (occurrences: 1)",
                warnings.format());
    }

    @Test
    void formatIncludesCount() {
        ParseWarnings warnings = new ParseWarnings();
        warnings.addFormatError("Timestamp", "12:34:56", "HH:mm:ss");
        warnings.addFormatError("Timestamp", "12:34:57", "HH:mm:ss");
        warnings.addFormatError("Timestamp", "12:34:58", "HH:mm:ss");

        assertTrue(warnings.format().contains("(occurrences: 3)"));
    }
}
