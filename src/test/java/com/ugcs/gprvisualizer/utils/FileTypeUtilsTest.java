package com.ugcs.gprvisualizer.utils;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeUtilsTest {

    @Test
    void isCsvFile_withNullFile_returnsFalse() {
        assertFalse(FileTypeUtils.isCsvFile((File) null));
    }

    @Test
    void isCsvFile_withValidCsvExtension_returnsTrue() {
        File file = new File("data.csv");
        assertTrue(FileTypeUtils.isCsvFile(file));
    }

    @Test
    void isCsvFile_withAscExtension_returnsTrue() {
        File file = new File("data.asc");
        assertTrue(FileTypeUtils.isCsvFile(file));
    }

    @Test
    void isCsvFile_withTxtExtension_returnsTrue() {
        File file = new File("data.txt");
        assertTrue(FileTypeUtils.isCsvFile(file));
    }

    @Test
    void isCsvFile_withWrongExtension_returnsFalse() {
        File file = new File("data.docx");
        assertFalse(FileTypeUtils.isCsvFile(file));
    }


    @Test
    void isCsvFile_withUpperCaseExtension_returnsTrue() {
        assertTrue(FileTypeUtils.isCsvFile("DATA.CSV"));
    }

    @Test
    void isCsvFile_withWhitespaceAndControlChars_returnsTrue() {
        assertTrue(FileTypeUtils.isCsvFile(" \tdata.csv\n"));
    }

    @Test
    void isCsvFile_withNullString_returnsFalse() {
        assertFalse(FileTypeUtils.isCsvFile((String) null));
    }


    @Test
    void isSgyFile_withNullFile_returnsFalse() {
        assertFalse(FileTypeUtils.isSgyFile((File) null));
    }

    @Test
    void isSgyFile_withValidSgyExtension_returnsTrue() {
        File file = new File("file.sgy");
        assertTrue(FileTypeUtils.isSgyFile(file));
    }

    @Test
    void isSgyFile_withWrongExtension_returnsFalse() {
        File file = new File("file.txt");
        assertFalse(FileTypeUtils.isSgyFile(file));
    }


    @Test
    void isSgyFile_withNullString_returnsFalse() {
        assertFalse(FileTypeUtils.isSgyFile((String) null));
    }

    @Test
    void isSgyFile_withEmptyString_returnsFalse() {
        assertFalse(FileTypeUtils.isSgyFile(""));
    }

    @Test
    void isSgyFile_withUpperCaseExtension_returnsTrue() {
        assertTrue(FileTypeUtils.isSgyFile("FILE.SGY"));
    }

    @Test
    void isSgyFile_withWhitespace_returnsTrue() {
        assertTrue(FileTypeUtils.isSgyFile("   file.sgy  "));
    }

    @Test
    void isDztFile_withNullFile_returnsFalse() {
        assertFalse(FileTypeUtils.isDztFile((File) null));
    }

    @Test
    void isDztFile_withValidDztExtension_returnsTrue() {
        File file = new File("scan.dzt");
        assertTrue(FileTypeUtils.isDztFile(file));
    }

    @Test
    void isDztFile_withWrongExtension_returnsFalse() {
        File file = new File("scan.pdf");
        assertFalse(FileTypeUtils.isDztFile(file));
    }

    @Test
    void isDztFile_withUpperCaseExtension_returnsTrue() {
        assertTrue(FileTypeUtils.isDztFile("SCAN.DZT"));
    }

    @Test
    void isDztFile_withWhitespace_returnsTrue() {
        assertTrue(FileTypeUtils.isDztFile("   scan.dzt  "));
    }

    @Test
    void isDztFile_withNullString_returnsFalse() {
        assertFalse(FileTypeUtils.isDztFile((String) null));
    }
}
