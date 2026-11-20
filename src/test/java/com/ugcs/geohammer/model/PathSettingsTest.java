package com.ugcs.geohammer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.util.UserPaths;

class PathSettingsTest {

    private static final String PYTHON_GROUP = "python";

    private static final String USER_TEMPLATES_GROUP = "userTemplates";

    private static final String USER_SCRIPTS_GROUP = "userScripts";

    private static final String PATH_KEY = "path";

    private static final Path GEOHAMMER_PATH = Path.of(UserPaths.HOME, ".geohammer");

    @TempDir
    Path root;

    // values are kept in memory: settings are written to disk only on save
    private final Settings settings = new Settings();

    private PathSettings pathSettings;

    @BeforeEach
    void setUp() {
        settings.setValue(PYTHON_GROUP, PATH_KEY, null);
        settings.setValue(USER_TEMPLATES_GROUP, PATH_KEY, null);
        settings.setValue(USER_SCRIPTS_GROUP, PATH_KEY, null);
        pathSettings = new PathSettings(settings);
    }

    @Test
    void storesPythonExecutable() {
        pathSettings.setPythonExecutable("/usr/bin/python3");

        assertEquals("/usr/bin/python3", pathSettings.getPythonExecutable());

        pathSettings.setPythonExecutable("");

        assertNull(pathSettings.getPythonExecutable());
    }

    @Test
    void usesDefaultFoldersInUserHome() {
        assertEquals(GEOHAMMER_PATH.resolve("templates"), pathSettings.getUserTemplatesPath());
        assertEquals(GEOHAMMER_PATH.resolve("scripts"), pathSettings.getUserScriptsPath());
    }

    @Test
    void storesChangedFolders() {
        Path templates = root.resolve("templates");
        Path scripts = root.resolve("scripts");

        pathSettings.setUserTemplatesPath(templates);
        pathSettings.setUserScriptsPath(scripts);

        assertEquals(templates, pathSettings.getUserTemplatesPath());
        assertEquals(scripts, pathSettings.getUserScriptsPath());
    }

    @Test
    void doesNotStoreDefaultFolder() {
        pathSettings.setUserScriptsPath(root);

        pathSettings.setUserScriptsPath(null);

        assertEquals(PathSettings.DEFAULT_SCRIPTS_PATH, pathSettings.getUserScriptsPath());
        assertNull(settings.getString(USER_SCRIPTS_GROUP, PATH_KEY));

        pathSettings.setUserScriptsPath(PathSettings.DEFAULT_SCRIPTS_PATH);

        assertNull(settings.getString(USER_SCRIPTS_GROUP, PATH_KEY));
    }
}
