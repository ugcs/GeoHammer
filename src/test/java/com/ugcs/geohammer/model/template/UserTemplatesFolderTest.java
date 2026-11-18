package com.ugcs.geohammer.model.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.Settings;

class UserTemplatesFolderTest {

    private static final String PREF_USER_TEMPLATES = "userTemplates";

    private static final String PREF_PATH = "path";

    private static final Path DEFAULT_PATH = Path.of(System.getProperty("user.home"), ".geohammer", "templates");

    @TempDir
    Path root;

    private final Settings settings = new Settings();

    @BeforeEach
    void setUp() {
        settings.setValue(PREF_USER_TEMPLATES, PREF_PATH, null);
    }

    @Test
    void defaultFolderIsInUserHome() {
        assertEquals(DEFAULT_PATH, UserTemplatesFolder.getDefaultPath());
        assertEquals(DEFAULT_PATH, new UserTemplatesFolder(settings).getPath());
    }

    @Test
    void usesStoredFolder() {
        settings.setValue(PREF_USER_TEMPLATES, PREF_PATH, root);

        UserTemplatesFolder folder = new UserTemplatesFolder(settings);

        assertEquals(root, folder.getPath());
    }

    @Test
    void storesChangedFolder() {
        UserTemplatesFolder folder = new UserTemplatesFolder(settings);

        assertTrue(folder.setPath(root));

        assertEquals(root.toString(), settings.getString(PREF_USER_TEMPLATES, PREF_PATH));
    }
}
