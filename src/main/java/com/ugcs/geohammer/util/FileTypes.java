package com.ugcs.geohammer.util;

import com.ugcs.geohammer.format.nmea.NmeaContentProbe;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FileTypes {

    private static final Set<String> SUPPORTED_EXTENSIONS = new LinkedHashSet<>(List.of(
            "sgy", "segy", "dzt", "nme", "nmea", "svlog",
            "asc", "csv", "log", "pos", "dat", "txt", "xyz"));

    private static final Map<String, Integer> EXTENSION_RANKS = rankExtensions(SUPPORTED_EXTENSIONS);

	private static final FileProbe TEXT_PROBE = new TextContentProbe();

    private static final FileProbe CSV_PROBE = new ExtensionProbe("csv", "asc", "pos", "dat");

    private static final FileProbe GPR_PROBE = new ExtensionProbe("sgy", "segy");

    private static final FileProbe DZT_PROBE = new ExtensionProbe("dzt");

    private static final FileProbe SVLOG_PROBE = new ExtensionProbe("svlog");

    private static final FileProbe NMEA_PROBE = new ExtensionProbe("nmea", "nme", "log", "txt");

    private static final FileProbe NMEA_CONTENT_PROBE = new NmeaContentProbe();

    private static final String POSITIONS_NAME_SUFFIX = "-position.csv";

    private FileTypes() {
    }

    public static boolean isTextFile(File file) {
        return TEXT_PROBE.matches(file);
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

    public static boolean isSvlogFile(File file) {
        return SVLOG_PROBE.matches(file);
    }

    public static boolean isNmeaFile(File file) {
        return NMEA_PROBE.matches(file) && NMEA_CONTENT_PROBE.matches(file);
    }

    public static boolean isSupportedFile(File file) {
        return file != null && SUPPORTED_EXTENSIONS.contains(getExtension(file));
    }

    private static Map<String, Integer> rankExtensions(Set<String> extensions) {
        Map<String, Integer> ranks = new HashMap<>(extensions.size());
        for (String extension : extensions) {
            if (Strings.isNullOrEmpty(extension)) {
                continue;
            }
            ranks.put(extension.toLowerCase(Locale.ROOT), ranks.size());
        }
        return ranks;
    }

    public static int getExtensionRank(File file) {
        if (file == null) {
            return EXTENSION_RANKS.size();
        }
        Integer rank = EXTENSION_RANKS.get(getExtension(file));
        return rank != null ? rank : EXTENSION_RANKS.size();
    }

    private static String getExtension(File file) {
        return Strings.nullToEmpty(FileNames.getExtension(file.getName()))
                .toLowerCase(Locale.ROOT);
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