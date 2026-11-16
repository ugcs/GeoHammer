package com.ugcs.geohammer;

import com.ugcs.geohammer.model.event.UserFolderChangedEvent;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Component
public class UserFolder {

    private static final Logger log = LoggerFactory.getLogger(UserFolder.class);

    private static final String DEFAULT_FOLDER = ".geohammer";

    private static final String TEMPLATES_FOLDER = "templates";

    private static final String PREF_USER_FOLDER = "userFolder";

    private static final String PREF_PATH = "path";

    private final Settings settings;

    private final ApplicationEventPublisher eventPublisher;

    private volatile Path path;

    @Autowired
    public UserFolder(Settings settings, ApplicationEventPublisher eventPublisher) {
        this(settings, eventPublisher, readPath(settings));
    }

    public UserFolder(Settings settings, ApplicationEventPublisher eventPublisher, Path path) {
        this.settings = Check.notNull(settings);
        this.eventPublisher = Check.notNull(eventPublisher);
        this.path = Check.notNull(path);
    }

    private static Path getDefaultPath() {
        return Path.of(System.getProperty("user.home"), DEFAULT_FOLDER);
    }

    private static Path readPath(Settings settings) {
        String path = settings.getString(PREF_USER_FOLDER, PREF_PATH);
        if (!Strings.isNullOrEmpty(path)) {
            try {
                return Path.of(path);
            } catch (InvalidPathException e) {
                log.warn("Invalid user folder in settings: {}", path, e);
            }
        }
        return getDefaultPath();
    }

    public Path getPath() {
        return path;
    }

    public void setPath(@Nullable Path path) {
        Path defaultPath = getDefaultPath();
        if (path == null) {
            path = defaultPath;
        }
        // the folder and its stored setting change together
        synchronized (this) {
            if (path.equals(this.path)) {
                return;
            }
            this.path = path;
            settings.setValue(PREF_USER_FOLDER, PREF_PATH, path.equals(defaultPath) ? null : path);
        }
        eventPublisher.publishEvent(new UserFolderChangedEvent(this));
    }

    public Path getTemplatesPath() {
        return path.resolve(TEMPLATES_FOLDER);
    }
}
