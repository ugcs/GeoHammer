package com.ugcs.gprvisualizer.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeUtilsTest {

    @Test
    void isCsvFile_withNullFile_returnsFalse() {
        assertFalse(FileTypeUtils.isCsvFile((File) null));
    }

    @Disabled
    @Test
    void isCsvFile_withValidCsvExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".csv");
        try {
            assertTrue(FileTypeUtils.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isCsvFile_withAscExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".asc");
        try {
            assertTrue(FileTypeUtils.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isCsvFile_withTxtExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".txt");
        try {
            assertTrue(FileTypeUtils.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isCsvFile_withWrongExtension_returnsFalse() throws IOException {
        File tempFile = File.createTempFile("data", ".docx");
        try {
            assertFalse(FileTypeUtils.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
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

    @Disabled
    @Test
    void isSgyFile_withValidSgyExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".sgy");
        try {
            assertTrue(FileTypeUtils.isSgyFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isSgyFile_withWrongExtension_returnsFalse() throws IOException {
        File tempFile = File.createTempFile("data", ".txt");
        try {
            assertFalse(FileTypeUtils.isSgyFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }


    @Test
    void isSgyFile_withNullString_returnsFalse() {
        assertFalse(FileTypeUtils.isSgyFile((String) null));
    }

    @Test
    void isSgyFile_withEmptyString_returnsFalse() {
        assertFalse(FileTypeUtils.isSgyFile(""));
    }

    @Disabled
    @Test
    void isSgyFile_withWhitespace_returnsTrue() {
        assertTrue(FileTypeUtils.isSgyFile("   file.sgy  "));
    }

    @Test
    void isDztFile_withNullFile_returnsFalse() {
        assertFalse(FileTypeUtils.isDztFile((File) null));
    }

    @Disabled
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

    @Disabled
    @Test
    void isDztFile_withWhitespace_returnsTrue() {
        assertTrue(FileTypeUtils.isDztFile("   scan.dzt  "));
    }

    @Test
    void isDztFile_withNullString_returnsFalse() {
        assertFalse(FileTypeUtils.isDztFile((String) null));
    }
}
