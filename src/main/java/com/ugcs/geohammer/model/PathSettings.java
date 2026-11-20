package com.ugcs.geohammer.model;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.UserPaths;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class PathSettings {

    public static final Path DEFAULT_TEMPLATES_PATH = UserPaths.GEOHAMMER_PATH.resolve("templates");

    public static final Path DEFAULT_SCRIPTS_PATH = UserPaths.GEOHAMMER_PATH.resolve("scripts");

    private static final String TEMPLATES_FOLDER_KEY = "userTemplates";

    private static final String SCRIPTS_FOLDER_KEY = "userScripts";

    private static final String PYTHON_KEY = "python";

    private static final String PATH_KEY = "path";

    private final Settings settings;

    public PathSettings(Settings settings) {
        this.settings = Check.notNull(settings);
    }

    // null when not set: the interpreter is then looked up in the system
    public @Nullable String getPythonExecutable() {
        return settings.getString(PYTHON_KEY, PATH_KEY);
    }

    public void setPythonExecutable(@Nullable String path) {
        settings.setValue(PYTHON_KEY, PATH_KEY, path);
    }

    public Path getUserTemplatesPath() {
        return settings.getPathOrDefault(TEMPLATES_FOLDER_KEY, PATH_KEY, DEFAULT_TEMPLATES_PATH);
    }

    // null selects the default folder
    public void setUserTemplatesPath(@Nullable Path path) {
        settings.setValue(TEMPLATES_FOLDER_KEY, PATH_KEY, path);
    }

    public Path getUserScriptsPath() {
        return settings.getPathOrDefault(SCRIPTS_FOLDER_KEY, PATH_KEY, DEFAULT_SCRIPTS_PATH);
    }

    // null selects the default folder
    public void setUserScriptsPath(@Nullable Path path) {
        settings.setValue(SCRIPTS_FOLDER_KEY, PATH_KEY, path);
    }
}
