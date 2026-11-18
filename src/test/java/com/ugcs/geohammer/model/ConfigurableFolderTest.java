package com.ugcs.geohammer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.Settings;

class ConfigurableFolderTest {

    private static final String PREF_GROUP = "testFolder";

    private static final String PREF_PATH = "path";

    @TempDir
    Path root;

    private Path defaultPath;

    // values are kept in memory: settings are written to disk only on save
    private final Settings settings = new Settings();

    @BeforeEach
    void setUp() {
        defaultPath = root.resolve("default");
        settings.setValue(PREF_GROUP, PREF_PATH, null);
    }

    @Test
    void usesDefaultFolderWhenNothingIsStored() {
        TestConfigurableFolder folder = new TestConfigurableFolder(settings, defaultPath);

        assertEquals(defaultPath, folder.getPath());
    }

    @Test
    void usesStoredFolder() {
        settings.setValue(PREF_GROUP, PREF_PATH, root);

        TestConfigurableFolder folder = new TestConfigurableFolder(settings, defaultPath);

        assertEquals(root, folder.getPath());
    }

    @Test
    void usesDefaultFolderForInvalidStoredValue() {
        settings.setValue(PREF_GROUP, PREF_PATH, "bad\u0000path");

        TestConfigurableFolder folder = new TestConfigurableFolder(settings, defaultPath);

        assertEquals(defaultPath, folder.getPath());
    }

    @Test
    void storesChangedFolder() {
        TestConfigurableFolder folder = new TestConfigurableFolder(settings, defaultPath);

        assertTrue(folder.setPath(root));

        assertEquals(root, folder.getPath());
        assertEquals(root.toString(), settings.getString(PREF_GROUP, PREF_PATH));
    }

    @Test
    void ignoresSameFolder() {
        settings.setValue(PREF_GROUP, PREF_PATH, root);
        TestConfigurableFolder folder = new TestConfigurableFolder(settings, defaultPath);
        // a changed folder would be stored again
        settings.setValue(PREF_GROUP, PREF_PATH, null);

        assertFalse(folder.setPath(root));

        assertNull(settings.getString(PREF_GROUP, PREF_PATH));
    }

    @Test
    void selectsDefaultFolderForNull() {
        TestConfigurableFolder folder = new TestConfigurableFolder(settings, defaultPath);
        folder.setPath(root);

        assertTrue(folder.setPath(null));

        assertEquals(defaultPath, folder.getPath());
        assertNull(settings.getString(PREF_GROUP, PREF_PATH));
    }

    @Test
    void createsFolder() {
        TestConfigurableFolder folder = new TestConfigurableFolder(settings, defaultPath);

        folder.createFolder();

        assertTrue(Files.isDirectory(defaultPath));
    }

    private static final class TestConfigurableFolder extends ConfigurableFolder {

        TestConfigurableFolder(Settings settings, Path defaultPath) {
            super(settings, PREF_GROUP, defaultPath);
        }

        boolean setPath(@Nullable Path path) {
            return updatePath(path);
        }
    }
}
