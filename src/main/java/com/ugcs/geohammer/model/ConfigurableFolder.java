package com.ugcs.geohammer.model;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public abstract class ConfigurableFolder {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableFolder.class);

    private static final String PREF_PATH = "path";

    private final Settings settings;

    private final String group;

    private final Path defaultPath;

    private volatile Path path;

    protected ConfigurableFolder(Settings settings, String group, Path defaultPath) {
        this.settings = Check.notNull(settings);
        this.group = Check.notNull(group);
        this.defaultPath = Check.notNull(defaultPath);
        this.path = readPath();
    }

    public Path getPath() {
        return path;
    }

    public void createFolder() {
        Path path = this.path;
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.warn("Cannot create folder {}", path, e);
        }
    }

    protected boolean updatePath(@Nullable Path path) {
        if (path == null) {
            path = defaultPath;
        }
        if (path.equals(this.path)) {
            return false;
        }
        settings.setValue(group, PREF_PATH, path.equals(defaultPath) ? null : path);
        this.path = path;
        return true;
    }

    private Path readPath() {
        String path = settings.getString(group, PREF_PATH);
        if (!Strings.isNullOrEmpty(path)) {
            try {
                return Path.of(path);
            } catch (InvalidPathException e) {
                log.warn("Invalid folder in settings group {}: {}", group, path, e);
            }
        }
        return defaultPath;
    }
}
