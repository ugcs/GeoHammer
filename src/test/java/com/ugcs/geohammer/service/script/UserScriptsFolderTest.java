package com.ugcs.geohammer.service.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
	}

	@Test
	void usesDefaultFolderWhenNothingIsStored() {
		UserScriptsFolder folder = new UserScriptsFolder(settings);

		assertEquals(DEFAULT_PATH, folder.getPath());
	}

	@Test
	void usesStoredFolder() {
		settings.setValue(PREF_USER_SCRIPTS, PREF_PATH, root);

		UserScriptsFolder folder = new UserScriptsFolder(settings);

		assertEquals(root, folder.getPath());
	}

	@Test
	void usesDefaultFolderForInvalidStoredValue() {
		settings.setValue(PREF_USER_SCRIPTS, PREF_PATH, "bad\u0000path");

		UserScriptsFolder folder = new UserScriptsFolder(settings);

		assertEquals(DEFAULT_PATH, folder.getPath());
	}

	@Test
	void storesChangedFolder() {
		UserScriptsFolder folder = new UserScriptsFolder(settings);

		assertTrue(folder.setPath(root));

		assertEquals(root, folder.getPath());
		assertEquals(root.toString(), settings.getString(PREF_USER_SCRIPTS, PREF_PATH));
	}

	@Test
	void ignoresSameFolder() {
		settings.setValue(PREF_USER_SCRIPTS, PREF_PATH, root);
		UserScriptsFolder folder = new UserScriptsFolder(settings);
		// a changed folder would be stored again
		settings.setValue(PREF_USER_SCRIPTS, PREF_PATH, null);

		assertFalse(folder.setPath(root));

		assertNull(settings.getString(PREF_USER_SCRIPTS, PREF_PATH));
	}

	@Test
	void selectsDefaultFolderForNull() {
		UserScriptsFolder folder = new UserScriptsFolder(settings);
		folder.setPath(root);

		assertTrue(folder.setPath(null));

		assertEquals(DEFAULT_PATH, folder.getPath());
		assertNull(settings.getString(PREF_USER_SCRIPTS, PREF_PATH));
	}
}
