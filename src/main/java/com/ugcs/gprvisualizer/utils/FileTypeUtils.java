package com.ugcs.gprvisualizer.utils;

import java.io.File;

public class FileTypeUtils {

    public static boolean isCsvFile(File file) {
        if (file == null) {
            return false;
        }
        return isCsvFile(file.getName());
    }

    public static boolean isCsvFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        String name = filename.trim().toLowerCase();
        return name.endsWith(".csv") || name.endsWith(".asc") || name.endsWith(".txt");
    }

    public static boolean isSgyFile(File file) {
        if (file == null) {
            return false;
        }
        return isSgyFile(file.getName());
    }

    public static boolean isSgyFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        return filename.trim().toLowerCase().endsWith(".sgy");
    }

    public static boolean isDztFile(File file) {
        if (file == null) {
            return false;
        }
        return isDztFile(file.getName());
    }

    public static boolean isDztFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        return filename.trim().toLowerCase().endsWith(".dzt");
    }
}