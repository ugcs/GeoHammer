package com.ugcs.geohammer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.model.event.UserFolderChangedEvent;

class UserFolderTest {

    private static final String PREF_USER_FOLDER = "userFolder";

    private static final String PREF_PATH = "path";

    @TempDir
    Path root;

    // values are kept in memory: settings are written to disk only on save
    private final Settings settings = new Settings();

    private final List<Object> events = new ArrayList<>();

    private UserFolder userFolder;

    @BeforeEach
    void setUp() {
        userFolder = new UserFolder(settings, events::add, root.resolve("user"));
    }

    @Test
    void resolvesTemplatesSubfolder() {
        assertEquals(root.resolve("user").resolve("templates"), userFolder.getTemplatesPath());
    }

    @Test
    void storesChangedPathAndPublishesEvent() {
        Path other = root.resolve("other");

        userFolder.setPath(other);

        assertEquals(other, userFolder.getPath());
        assertEquals(other.resolve("templates"), userFolder.getTemplatesPath());
        assertEquals(other.toString(), settings.getString(PREF_USER_FOLDER, PREF_PATH));
        assertEquals(1, events.size());
        assertInstanceOf(UserFolderChangedEvent.class, events.getFirst());
    }

    @Test
    void ignoresSamePath() {
        userFolder.setPath(root.resolve("user"));

        assertEquals(0, events.size());
    }

    @Test
    void selectsDefaultPathForNull() {
        userFolder.setPath(root.resolve("other"));

        userFolder.setPath(null);

        assertEquals(Path.of(System.getProperty("user.home"), ".geohammer"), userFolder.getPath());
        assertNull(settings.getString(PREF_USER_FOLDER, PREF_PATH));
        assertEquals(2, events.size());
    }
}
