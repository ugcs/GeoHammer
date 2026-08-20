package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MetaFileNaming {

    private static final Logger log = LoggerFactory.getLogger(MetaFileNaming.class);

    private static final String META_FILE_EXTENSION = ".geohammer";

    private MetaFileNaming() {
    }

    public static boolean isMeta(File file) {
        return file.getName().endsWith(META_FILE_EXTENSION);
    }

    public static Path getMetaPath(File source) {
        Check.notNull(source);

        String metaFileName = source.getName() + META_FILE_EXTENSION;

        return new File(source.getParentFile(), metaFileName).toPath();
    }

    public static Path getLegacyMetaPath(File source) {
        Check.notNull(source);

        String sourceBase = FileNames.removeExtension(source.getName());
        String metaFileName = Strings.nullToEmpty(sourceBase) + META_FILE_EXTENSION;

        return new File(source.getParentFile(), metaFileName).toPath();
    }

    public static @Nullable Path findMetaPath(File source) {
        Check.notNull(source);

        Path metaPath = getMetaPath(source);
        if (Files.exists(metaPath)) {
            return metaPath;
        }
        Path legacyMetaPath = getLegacyMetaPath(source);
        return Files.exists(legacyMetaPath) ? legacyMetaPath : null;
    }

    public static List<File> getSources(File metaFile) {
        Check.notNull(metaFile);

        String base = FileNames.removeExtension(metaFile.getName());
        if (Strings.isNullOrEmpty(base)) {
            return List.of();
        }

        File source = getSource(metaFile.getParentFile(), base);
        return source != null
                ? List.of(source)
                : getLegacySources(metaFile.getParentFile(), base);
    }

    private static @Nullable File getSource(@Nullable File parent, String base) {
        File source = new File(parent, base);
        return source.isFile() && !isMeta(source) ? source : null;
    }

    private static List<File> getLegacySources(@Nullable File parent, String base) {
        if (parent == null) {
            return List.of();
        }
        File[] files = parent.listFiles();
        if (files == null) {
            return List.of();
        }
        List<File> sources = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()
                    && !isMeta(file)
                    && base.equalsIgnoreCase(FileNames.removeExtension(file.getName()))) {
                sources.add(file);
            }
        }
        sources.sort(Comparator.comparingInt(FileTypes::getExtensionRank)
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return sources;
    }

    public static void migrateLegacyMeta(File source) {
        Check.notNull(source);

        Path metaPath = getMetaPath(source);
        Path legacyMetaPath = getLegacyMetaPath(source);
        // sources without an extension share a single meta path
        if (legacyMetaPath.equals(metaPath)
                || Files.exists(metaPath)
                || !Files.exists(legacyMetaPath)) {
            return;
        }
        try {
            Files.move(legacyMetaPath, metaPath);
            log.info("Meta file {} renamed to {}", legacyMetaPath, metaPath);
        } catch (IOException e) {
            log.warn("Cannot rename meta file {}", legacyMetaPath, e);
        }
    }

    public static void deleteLegacyMeta(File source) {
        Check.notNull(source);

        Path legacyMetaPath = getLegacyMetaPath(source);
        // sources without an extension share a single meta path
        if (legacyMetaPath.equals(getMetaPath(source))) {
            return;
        }
        try {
            if (Files.deleteIfExists(legacyMetaPath)) {
                log.debug("Legacy meta file {} deleted", legacyMetaPath);
            }
        } catch (IOException e) {
            log.warn("Cannot delete meta file {}", legacyMetaPath, e);
        }
    }
}
