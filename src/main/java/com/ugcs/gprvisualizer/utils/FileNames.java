package com.ugcs.gprvisualizer.utils;

import com.google.common.base.Strings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileNames {

    private static final Pattern GPR_PART_SUFFIX = Pattern.compile("_(\\d{3})$");

    private FileNames() {
    }

    public static String getBaseName(String fileName) {
        fileName = Strings.nullToEmpty(fileName);
        int k = fileName.lastIndexOf('.');
        if (k != -1) {
            fileName = fileName.substring(0, k);
        }
        return fileName;
    }

    public static String getExtension(String fileName) {
        fileName = Strings.nullToEmpty(fileName);
        int k = fileName.lastIndexOf('.');
        return k != -1 ? fileName.substring(k + 1) : "";
    }

    public static int getGprPart(String fileName) {
        fileName = Strings.nullToEmpty(fileName);
        String baseName = getBaseName(fileName);

        Matcher matcher = GPR_PART_SUFFIX.matcher(baseName);
        if (matcher.find()) {
            String digits = matcher.group(1);
            return Integer.parseInt(digits);
        }
        return -1;
    }

    public static String setGprPart(String fileName, int part) {
        Check.condition(part >= 0);

        fileName = Strings.nullToEmpty(fileName);
        String baseName = getBaseName(fileName);

        String partSuffix = "_" + String.format("%03d", part);

        Matcher matcher = GPR_PART_SUFFIX.matcher(baseName);
        if (matcher.find()) {
            StringBuilder result = new StringBuilder();
            matcher.appendReplacement(result, partSuffix);
            matcher.appendTail(result);
            baseName = result.toString();
        } else {
            baseName += partSuffix;
        }
        // restore extension
        String extension = getExtension(fileName);
        return !Strings.isNullOrEmpty(extension)
                ? baseName + "." + extension
                : baseName;
    }
}
