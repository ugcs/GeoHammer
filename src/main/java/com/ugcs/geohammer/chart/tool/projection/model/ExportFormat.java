package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

import java.nio.file.Path;
import java.util.Locale;

public enum ExportFormat {

    LAS("LAS", "las"),
    PLY("PLY", "ply");

    private final String displayName;

    private final String extension;

    ExportFormat(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }

    public static ExportFormat defaultFormat() {
        return ExportFormat.LAS;
    }

    public static ExportFormat of(Path path) {
        return path != null ? ExportFormat.of(path.toAbsolutePath().toString()) : null;
    }

    public static ExportFormat of(String path) {
        if (Strings.isNullOrBlank(path)) {
            return null;
        }
        String extension = Nulls.toEmpty(FileNames.getExtension(path)).toLowerCase(Locale.ROOT);
        for (ExportFormat format : values()) {
            if (format.getExtension().equals(extension)) {
                return format;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
