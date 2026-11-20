package com.ugcs.geohammer.service.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptPathsTest {

	@TempDir
	Path root;

	private Path appScripts;

	private Path userScripts;

	private ScriptPaths scriptPaths;

	@BeforeEach
	void setUp() throws IOException {
		appScripts = Files.createDirectories(root.resolve("app").resolve("scripts"));
		userScripts = root.resolve("user").resolve("scripts");
		scriptPaths = new ScriptPaths(userScripts, appScripts);
	}

	@Test
	void createsUserScriptsFolder() {
		assertTrue(Files.isDirectory(userScripts));
		assertEquals(userScripts, scriptPaths.getUserScriptsPath());
		assertEquals(appScripts, scriptPaths.getScriptsPath());
	}

	@Test
	void findsUserScriptBeforeBuiltIn() throws IOException {
		Files.writeString(appScripts.resolve("filter.py"), "");
		Files.writeString(userScripts.resolve("filter.py"), "");

		assertEquals(userScripts.resolve("filter.py"), scriptPaths.findScriptFile("filter.py"));
	}

	@Test
	void findsBuiltInScriptMissingInUserFolder() throws IOException {
		Files.writeString(appScripts.resolve("filter.py"), "");

		assertEquals(appScripts.resolve("filter.py"), scriptPaths.findScriptFile("filter.py"));
	}

	@Test
	void failsToFindMissingScript() {
		assertThrows(IOException.class, () -> scriptPaths.findScriptFile("filter.py"));
	}

	@Test
	void createsAndUsesChangedUserScriptsFolder() throws IOException {
		Path otherScripts = root.resolve("other").resolve("scripts");

		scriptPaths.setUserScriptsPath(otherScripts);
		Files.writeString(otherScripts.resolve("filter.py"), "");

		assertTrue(Files.isDirectory(otherScripts));
		assertEquals(otherScripts, scriptPaths.getUserScriptsPath());
		assertEquals(otherScripts.resolve("filter.py"), scriptPaths.findScriptFile("filter.py"));
	}
}
