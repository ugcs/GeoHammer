package com.ugcs.gprvisualizer.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileNames {

    private static final Pattern GPR_PART_SUFFIX = Pattern.compile("_\\d{3}(_\\d)*$");

    private FileNames() {
    }

    public static String removeExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int k = fileName.lastIndexOf('.');
        return k != -1 ? fileName.substring(0, k) : fileName;
    }

    public static String getExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int k = fileName.lastIndexOf('.');
        return k != -1 ? fileName.substring(k + 1) : "";
    }

    public static boolean hasGprPartSuffix(String fileName) {
        if (fileName == null) {
            return false;
        }
        String baseName = removeExtension(fileName);
        Matcher matcher = GPR_PART_SUFFIX.matcher(baseName);
        return matcher.find();
    }
}
