package com.ugcs.geohammer.model.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void usesDefaultFolderWhenNothingIsStored() {
        UserTemplatesFolder folder = new UserTemplatesFolder(settings);

        assertEquals(DEFAULT_PATH, folder.getPath());
    }

    @Test
    void usesStoredFolder() {
        settings.setValue(PREF_USER_TEMPLATES, PREF_PATH, root);

        UserTemplatesFolder folder = new UserTemplatesFolder(settings);

        assertEquals(root, folder.getPath());
    }

    @Test
    void usesDefaultFolderForInvalidStoredValue() {
        settings.setValue(PREF_USER_TEMPLATES, PREF_PATH, "bad\u0000path");

        UserTemplatesFolder folder = new UserTemplatesFolder(settings);

        assertEquals(DEFAULT_PATH, folder.getPath());
    }

    @Test
    void storesChangedFolder() {
        UserTemplatesFolder folder = new UserTemplatesFolder(settings);

        folder.setPath(root);

        assertEquals(root, folder.getPath());
        assertEquals(root.toString(), settings.getString(PREF_USER_TEMPLATES, PREF_PATH));
    }

    @Test
    void ignoresSameFolder() {
        settings.setValue(PREF_USER_TEMPLATES, PREF_PATH, root);
        UserTemplatesFolder folder = new UserTemplatesFolder(settings);
        // a changed folder would be stored again
        settings.setValue(PREF_USER_TEMPLATES, PREF_PATH, null);

        folder.setPath(root);

        assertNull(settings.getString(PREF_USER_TEMPLATES, PREF_PATH));
    }

    @Test
    void selectsDefaultFolderForNull() {
        UserTemplatesFolder folder = new UserTemplatesFolder(settings);
        folder.setPath(root);

        folder.setPath(null);

        assertEquals(DEFAULT_PATH, folder.getPath());
        assertNull(settings.getString(PREF_USER_TEMPLATES, PREF_PATH));
    }
}
