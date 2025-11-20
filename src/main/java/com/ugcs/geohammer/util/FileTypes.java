package com.ugcs.geohammer.util;

import java.io.File;
import java.util.Objects;
import java.util.Set;

public final class FileTypes {

    private static final Set<String> CSV_EXTENSIONS = Set.of("csv", "asc", "txt", "pos");

    private static final Set<String> GPR_EXTENSIONS = Set.of("sgy");

    private static final Set<String> DZT_EXTENSIONS = Set.of("dzt");

    private static final Set<String> SVLOG_EXTENSIONS = Set.of("svlog");

    private static final Set<String> KML_EXTENSIONS = Set.of("kml");

    private static final Set<String> CONST_POINTS_EXTENSIONS = Set.of("constpoints");

    private static String POSITIONS_NAME_SUFFIX = "-position.csv";

    private FileTypes() {
    }

    public static boolean extensionMatches(String fileName, Set<String> extensions) {
        String extension = Strings.nullToEmpty(FileNames.getExtension(fileName))
                .toLowerCase();
        return Nulls.toEmpty(extensions).contains(extension);
    }

    public static boolean isCsvFile(File file) {
        return file != null && isCsvFile(file.getName());
    }

    public static boolean isCsvFile(String fileName) {
        return extensionMatches(fileName, CSV_EXTENSIONS);
    }

    public static boolean isGprFile(File file) {
        return file != null && isGprFile(file.getName());
    }

    public static boolean isGprFile(String filename) {
        return extensionMatches(filename, GPR_EXTENSIONS);
    }

    public static boolean isDztFile(File file) {
        return file != null && isDztFile(file.getName());
    }

    public static boolean isDztFile(String fileName) {
        return extensionMatches(fileName, DZT_EXTENSIONS);
    }

    public static boolean isSvlogFile(File file) {
        return file != null && isSvlogFile(file.getName());
    }

    public static boolean isSvlogFile(String fileName) {
        return extensionMatches(fileName, SVLOG_EXTENSIONS);
    }

    public static boolean isKmlFile(File file) {
        return file != null && isKmlFile(file.getName());
    }

    public static boolean isKmlFile(String fileName) {
        return extensionMatches(fileName, KML_EXTENSIONS);
    }

    public static boolean isConstPointFile(File file) {
        return file != null && isConstPointFile(file.getName());
    }

    public static boolean isConstPointFile(String fileName) {
        return extensionMatches(fileName, CONST_POINTS_EXTENSIONS);
    }

    public static boolean isPositionFile(File file) {
        return file != null && isPositionFile(file.getName());
    }

    public static boolean isPositionFile(String fileName) {
        return Strings.nullToEmpty(fileName)
                .toLowerCase()
                .endsWith(POSITIONS_NAME_SUFFIX);
    }

    public static String getPositionFileNameBase(String fileName) {
        Check.condition(isPositionFile(fileName));

        int baseLength = fileName.length() - POSITIONS_NAME_SUFFIX.length();
        return fileName
                .toLowerCase()
                .substring(0, baseLength);
    }

    public static boolean isPositionFileFor(File positionFile, File file) {
        if (!isPositionFile(positionFile)) {
            return false;
        }
        if (!isGprFile(file)) {
            return false;
        }
        if (!Objects.equals(positionFile.getParent(), file.getParent())) {
            return false;
        }
        String fileNameBase = getPositionFileNameBase(positionFile.getName());
        return Nulls.toEmpty(file.getName())
                .toLowerCase()
                .startsWith(fileNameBase);
    }
}