package com.ugcs.geohammer.util;

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

    public static String addSuffix(String fileName, String suffix) {
        return addSuffix(fileName, suffix, "-");
    }

    public static String addSuffix(String fileName, String suffix, String separator) {
        if (fileName == null) {
            return null;
        }
        if (Strings.isNullOrEmpty(suffix)) {
            return fileName;
        }
        int k = fileName.lastIndexOf('.');
        if (k != -1) {
            String base = fileName.substring(0, k);
            String extension = fileName.substring(k);
            return base + separator + suffix + extension;
        } else {
            return fileName + separator + suffix;
        }
    }
}
