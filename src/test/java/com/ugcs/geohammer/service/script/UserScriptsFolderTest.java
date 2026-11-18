package com.ugcs.geohammer.service.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.Settings;

class UserScriptsFolderTest {

	private static final String PREF_USER_SCRIPTS = "userScripts";

	private static final String PREF_PATH = "path";

	private static final Path DEFAULT_PATH = Path.of(System.getProperty("user.home"), ".geohammer", "scripts");

	@TempDir
	Path root;

	private final Settings settings = new Settings();

	@BeforeEach
	void setUp() {
		settings.setValue(PREF_USER_SCRIPTS, PREF_PATH, null);
	}

	@Test
	void defaultFolderIsInUserHome() {
		assertEquals(DEFAULT_PATH, UserScriptsFolder.getDefaultPath());
		assertEquals(DEFAULT_PATH, new UserScriptsFolder(settings).getPath());
	}

	@Test
	void usesStoredFolder() {
		settings.setValue(PREF_USER_SCRIPTS, PREF_PATH, root);

		UserScriptsFolder folder = new UserScriptsFolder(settings);

		assertEquals(root, folder.getPath());
	}

	@Test
	void storesChangedFolder() {
		UserScriptsFolder folder = new UserScriptsFolder(settings);

		assertTrue(folder.setPath(root));

		assertEquals(root.toString(), settings.getString(PREF_USER_SCRIPTS, PREF_PATH));
	}
}
