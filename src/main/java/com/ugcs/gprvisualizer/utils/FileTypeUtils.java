package com.ugcs.gprvisualizer.utils;

import java.io.File;

public class FileTypeUtils {

    public static boolean isCsvFile(File file) {
        return isCsvFile(file.getName());
    }

    public static boolean isCsvFile(String filename) {
        String name = filename.toLowerCase();
        return name.endsWith(".csv") || name.endsWith(".asc") || name.endsWith(".txt");
    }

    public static boolean isSgyFile(File file) {
        return isSgyFile(file.getName());
    }

    public static boolean isSgyFile(String filename) {
        return filename.toLowerCase().endsWith(".sgy");
    }

    public static boolean isDztFile(File file) {
        return isDztFile(file.getName());
    }

    public static boolean isDztFile(String filename) {
        return filename.toLowerCase().endsWith(".dzt");
    }
}