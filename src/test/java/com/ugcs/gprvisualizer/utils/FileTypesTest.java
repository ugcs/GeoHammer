package com.ugcs.gprvisualizer.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileTypesTest {

    @Test
    void isCsvFileFile_withNull_returnsFalse() {
        assertFalse(FileTypes.isCsvFile((File) null));
    }

    @Disabled
    @Test
    void isCsvFile_withValidCsvFileExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".csv");
        try {
            assertTrue(FileTypes.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isCsv_File_withAscExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".asc");
        try {
            assertTrue(FileTypes.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isCsv_File_withTxtExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".txt");
        try {
            assertTrue(FileTypes.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isCsv_File_withWrongExtension_returnsFalse() throws IOException {
        File tempFile = File.createTempFile("data", ".docx");
        try {
            assertFalse(FileTypes.isCsvFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isCsv_File_withWhitespaceAndControlChars_returnsTrue() {
        assertTrue(FileTypes.isCsvFile(" \tdata.csv\n"));
    }

    @Test
    void isCsv_File_withNullString_returnsFalse() {
        assertFalse(FileTypes.isCsvFile((String) null));
    }


    @Test
    void isGprFileFile_withNull_returnsFalse() {
        assertFalse(FileTypes.isGprFile((File) null));
    }

    @Disabled
    @Test
    void isSgyFile_withValidGprExtension_returnsTrue() throws IOException {
        File tempFile = File.createTempFile("data", ".sgy");
        try {
            assertTrue(FileTypes.isGprFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }

    @Disabled
    @Test
    void isGpr_File_withWrongExtension_returnsFalse() throws IOException {
        File tempFile = File.createTempFile("data", ".txt");
        try {
            assertFalse(FileTypes.isGprFile(tempFile));
        } finally {
            tempFile.deleteOnExit();
        }
    }


    @Test
    void isGpr_File_withNullString_returnsFalse() {
        assertFalse(FileTypes.isGprFile((String) null));
    }

    @Test
    void isGpr_File_withEmptyString_returnsFalse() {
        assertFalse(FileTypes.isGprFile(""));
    }

    @Disabled
    @Test
    void isGpr_File_withWhitespace_returnsTrue() {
        assertTrue(FileTypes.isGprFile("   file.sgy  "));
    }

    @Test
    void isDztFileFile_withNull_returnsFalse() {
        assertFalse(FileTypes.isDztFile((File) null));
    }

    @Disabled
    @Test
    void isDztFile_withValidDztFileExtension_returnsTrue() {
        File file = new File("scan.dzt");
        assertTrue(FileTypes.isDztFile(file));
    }

    @Test
    void isDzt_File_withWrongExtension_returnsFalse() {
        File file = new File("scan.pdf");
        assertFalse(FileTypes.isDztFile(file));
    }

    @Disabled
    @Test
    void isDzt_File_withWhitespace_returnsTrue() {
        assertTrue(FileTypes.isDztFile("   scan.dzt  "));
    }

    @Test
    void isDzt_File_withNullString_returnsFalse() {
        assertFalse(FileTypes.isDztFile((String) null));
    }
}
