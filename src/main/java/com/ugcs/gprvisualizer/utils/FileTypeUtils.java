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
        String name = normalize(filename);
        System.out.println("Normalized name: [" + name + "]");
        boolean result = name.endsWith(".csv") || name.endsWith(".asc") || name.endsWith(".txt");
        System.out.println("CSV result: " + result);
        return result;
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
        return normalize(filename).endsWith(".sgy");
    }

    public static boolean isDztFile(File file) {
        if (file == null) {
            return false;
        }
        return isDztFile(file.getName());
    }

    public static boolean isDztFile(String filename) {
        return normalize(filename).endsWith(".dzt");
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\p{C}", "").trim().toLowerCase();
    }

}