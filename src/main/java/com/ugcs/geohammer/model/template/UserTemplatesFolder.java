package com.ugcs.geohammer.model.template;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.UserDataFolder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Component
public class UserTemplatesFolder {

    private static final Logger log = LoggerFactory.getLogger(UserTemplatesFolder.class);

    private static final String DEFAULT_TEMPLATES_FOLDER = "templates";

    private static final String PREF_USER_TEMPLATES = "userTemplates";

    private static final String PREF_PATH = "path";

    private final Settings settings;

    private volatile Path path;

    public UserTemplatesFolder(Settings settings) {
        this.settings = Check.notNull(settings);
        this.path = readPath(settings);
    }

    public static Path getDefaultPath() {
        return UserDataFolder.getPath().resolve(DEFAULT_TEMPLATES_FOLDER);
    }

    private static Path readPath(Settings settings) {
        String path = settings.getString(PREF_USER_TEMPLATES, PREF_PATH);
        if (!Strings.isNullOrEmpty(path)) {
            try {
                return Path.of(path);
            } catch (InvalidPathException e) {
                log.warn("Invalid templates folder in settings: {}", path, e);
            }
        }
        return getDefaultPath();
    }

    public Path getPath() {
        return path;
    }

    boolean setPath(@Nullable Path path) {
        Path defaultPath = getDefaultPath();
        if (path == null) {
            path = defaultPath;
        }
        if (path.equals(this.path)) {
            return false;
        }
        settings.setValue(PREF_USER_TEMPLATES, PREF_PATH, path.equals(defaultPath) ? null : path);
        this.path = path;
        return true;
    }
}
