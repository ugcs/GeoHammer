package com.ugcs.geohammer.format.meta;

import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.util.GsonConfig;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MetaFiles {

    private static final Logger log = LoggerFactory.getLogger(MetaFiles.class);

    private static final String META_FILE_EXTENSION = ".geohammer";

    private MetaFiles() {
    }

    public static boolean isMeta(@Nullable File file) {
        if (file == null) {
            return false;
        }
        return file.getName()
                .toLowerCase(Locale.ROOT)
                .endsWith(META_FILE_EXTENSION);
    }

    public static Path getMetaPath(File source) {
        Check.notNull(source);

        String metaFileName = source.getName() + META_FILE_EXTENSION;
        return source.toPath().resolveSibling(metaFileName);
    }

    private static Path getLegacyMetaPath(File source) {
        Check.notNull(source);

        String sourceBase = FileNames.removeExtension(source.getName());
        String metaFileName = Strings.nullToEmpty(sourceBase) + META_FILE_EXTENSION;
        return source.toPath().resolveSibling(metaFileName);
    }

    // locates meta file;
    // moves legacy meta to canonical path if needed
    public static @Nullable Path resolveMetaPath(File source) {
        Check.notNull(source);

        Path metaPath = getMetaPath(source);
        if (Files.isRegularFile(metaPath)) {
            return metaPath;
        }
        Path legacyMetaPath = getLegacyMetaPath(source);
        if (Files.isRegularFile(legacyMetaPath)) {
            return moveMeta(legacyMetaPath, metaPath);
        }
        return null;
    }

    private static Path moveMeta(Path from, Path to) {
        Check.notNull(from);
        Check.notNull(to);

        if (from.equals(to)) {
            return from;
        }

        try {
            Files.move(from, to);
            log.info("Meta file {} renamed to {}", from, to);
            return to;
        } catch (IOException e) {
            log.warn("Cannot rename meta file {}", from, e);
            return from;
        }
    }

    public static List<File> resolveSources(File metaFile) {
        File source = resolveSource(metaFile);
        if (source != null) {
            return List.of(source);
        }
        return resolveLegacySources(metaFile);
    }

    private static @Nullable File resolveSource(File metaFile) {
        if (metaFile == null) {
            return null;
        }
        File file = new File(
                metaFile.getParentFile(),
                FileNames.removeExtension(metaFile.getName()));
        return file.isFile() && !isMeta(file) ? file : null;
    }

    private static List<File> resolveLegacySources(File metaFile) {
        if (metaFile == null) {
            return List.of();
        }
        File parent = metaFile.getParentFile();
        if (parent == null) {
            return List.of();
        }
        File[] files = parent.listFiles();
        if (files == null) {
            return List.of();
        }

        String metaBase = FileNames.removeExtension(metaFile.getName());
        List<File> sources = new ArrayList<>();
        for (File file : files) {
            if (isMeta(file) || !file.isFile()) {
                continue;
            }
            String fileBase = FileNames.removeExtension(file.getName());
            if (Strings.equalsIgnoreCase(fileBase, metaBase)) {
                sources.add(file);
            }
        }

        sources.sort(Comparator.comparingInt(FileTypes::getExtensionRank)
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        return sources;
    }

    public static @Nullable Meta readMetaOf(File source, ColumnSchema columnSchema) throws IOException {
        Check.notNull(source);

        Path metaPath = MetaFiles.resolveMetaPath(source);
        if (metaPath == null) {
            return null;
        }
        return readMeta(metaPath, columnSchema);
    }

    public static void writeMetaOf(File source, @Nullable Meta meta) throws IOException {
        Check.notNull(source);

        Path metaPath = MetaFiles.getMetaPath(source);
        writeMeta(meta, metaPath);
    }

    public static @Nullable Meta readMeta(Path path, ColumnSchema columnSchema) throws IOException {
        Check.notNull(path);

        MetaDocument metaDocument = readMetaDocument(path);
        return MetaDocument.toMeta(metaDocument, columnSchema);
    }

    public static void writeMeta(@Nullable Meta meta, Path path) throws IOException {
        Check.notNull(path);

        if (meta != null) {
            MetaDocument metaDocument = MetaDocument.of(meta);
            writeMetaDocument(metaDocument, path);
        } else {
            Files.deleteIfExists(path);
        }
    }

    public static @Nullable MetaDocument readMetaDocument(Path path) throws IOException {
        Check.notNull(path);

        try (Reader reader = Files.newBufferedReader(path)) {
            return GsonConfig.GSON.fromJson(reader, MetaDocument.class);
        }
    }

    public static void writeMetaDocument(MetaDocument metaDocument, Path path) throws IOException {
        Check.notNull(metaDocument);
        Check.notNull(path);

        try (Writer writer = Files.newBufferedWriter(path)) {
            GsonConfig.GSON.toJson(metaDocument, MetaDocument.class, writer);
        }
    }
}
