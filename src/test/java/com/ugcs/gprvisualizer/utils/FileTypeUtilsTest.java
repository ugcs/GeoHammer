package com.ugcs.gprvisualizer.utils;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeUtilsTest {

    @Test
    void testIsCsvFileWithFile() {
        assertTrue(FileTypeUtils.isCsvFile(new File("data.csv")));
        assertTrue(FileTypeUtils.isCsvFile(new File("data.TXT")));
        assertFalse(FileTypeUtils.isCsvFile(new File("data.sgy")));
        assertFalse(FileTypeUtils.isCsvFile(new File("data.dzt")));
        assertFalse(FileTypeUtils.isCsvFile(new File("data")));
    }

    @Test
    void testIsCsvFileWithString() {
        assertTrue(FileTypeUtils.isCsvFile("file.csv"));
        assertTrue(FileTypeUtils.isCsvFile("file.asc"));
        assertTrue(FileTypeUtils.isCsvFile("file.txt"));
        assertFalse(FileTypeUtils.isCsvFile("file.sgy"));
        assertFalse(FileTypeUtils.isCsvFile("file.dzt"));
        assertFalse(FileTypeUtils.isCsvFile("file"));
    }

    @Test
    void testIsSgyFileWithFile() {
        assertTrue(FileTypeUtils.isSgyFile(new File("trace.sgy")));
        assertFalse(FileTypeUtils.isSgyFile(new File("trace.csv")));
        assertFalse(FileTypeUtils.isSgyFile(new File("trace.dzt")));
        assertFalse(FileTypeUtils.isSgyFile(new File("trace")));
    }

    @Test
    void testIsSgyFileWithString() {
        assertTrue(FileTypeUtils.isSgyFile("trace.sgy"));
        assertFalse(FileTypeUtils.isSgyFile("trace.csv"));
        assertFalse(FileTypeUtils.isSgyFile("trace.dzt"));
        assertFalse(FileTypeUtils.isSgyFile("trace"));
    }

    @Test
    void testIsDztFileWithFile() {
        assertTrue(FileTypeUtils.isDztFile(new File("scan.dzt")));
        assertFalse(FileTypeUtils.isDztFile(new File("scan.csv")));
        assertFalse(FileTypeUtils.isDztFile(new File("scan.sgy")));
        assertFalse(FileTypeUtils.isDztFile(new File("scan")));
    }

    @Test
    void testIsDztFileWithString() {
        assertTrue(FileTypeUtils.isDztFile("scan.dzt"));
        assertFalse(FileTypeUtils.isDztFile("scan.csv"));
        assertFalse(FileTypeUtils.isDztFile("scan.sgy"));
        assertFalse(FileTypeUtils.isDztFile("scan"));
    }

    @Test
    void debugCsvLowercaseCheck() {
        String name = "data.csv";
        String lower = name.trim().toLowerCase();
        System.out.println("Lower: [" + lower + "]");
        assertTrue(lower.endsWith(".csv"));
    }
}