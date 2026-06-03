package com.ugcs.geohammer.util;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class FileTypesTest {

    @Test
    void isCsvFileFile_withNull_returnsFalse() {
        assertFalse(FileTypes.isCsvFile(null));
    }

    @Test
    void isCsv_File_withNullString_returnsFalse() {
        assertFalse(FileTypes.isCsvFile(null));
    }

    @Test
    void isGprFileFile_withNull_returnsFalse() {
        assertFalse(FileTypes.isGprFile(null));
    }

    @Test
    void isGpr_File_withNullString_returnsFalse() {
        assertFalse(FileTypes.isGprFile(null));
    }

    @Test
    void isGpr_File_withEmptyString_returnsFalse() {
        assertFalse(FileTypes.isGprFile(new File("")));
    }

    @Test
    void isDztFileFile_withNull_returnsFalse() {
        assertFalse(FileTypes.isDztFile(null));
    }

    @Test
    void isDzt_File_withWrongExtension_returnsFalse() {
        File file = new File("scan.pdf");
        assertFalse(FileTypes.isDztFile(file));
    }

    @Test
    void isDzt_File_withNullString_returnsFalse() {
        assertFalse(FileTypes.isDztFile(null));
    }
}
