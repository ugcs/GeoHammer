package com.ugcs.geohammer.util;

import com.ugcs.geohammer.format.nmea.NmeaContentProbe;

import java.io.File;
import java.util.Objects;

public final class FileTypes {

    private static final FileProbe CSV_PROBE = new ExtensionProbe("csv", "asc", "pos");

    private static final FileProbe GPR_PROBE = new ExtensionProbe("sgy");

    private static final FileProbe DZT_PROBE = new ExtensionProbe("dzt");

    private static final FileProbe SVLOG_PROBE = new ExtensionProbe("svlog");

    private static final FileProbe NMEA_PROBE = new ExtensionProbe("nmea", "nme", "log", "txt");

    private static final FileProbe NMEA_CONTENT_PROBE = new NmeaContentProbe();

    private static final String POSITIONS_NAME_SUFFIX = "-position.csv";

    private FileTypes() {
    }

    public static boolean isCsvFile(File file) {
        return CSV_PROBE.matches(file);
    }

    public static boolean isGprFile(File file) {
        return GPR_PROBE.matches(file);
    }

    public static boolean isDztFile(File file) {
        return DZT_PROBE.matches(file);
    }

    public static boolean isTraceFile(File file) {
        return isGprFile(file) || isDztFile(file);
    }

    public static boolean isSvlogFile(File file) {
        return SVLOG_PROBE.matches(file);
    }

    public static boolean isNmeaFile(File file) {
        return NMEA_PROBE.matches(file) && NMEA_CONTENT_PROBE.matches(file);
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