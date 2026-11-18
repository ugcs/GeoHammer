package com.ugcs.geohammer.service.script;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.Settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class JsonScriptMetadataLoaderTest {

	private static final String PREF_USER_SCRIPTS = "userScripts";

	private static final String PREF_PATH = "path";

	@TempDir
	Path root;

	@Test
	void loadsScriptsFromDirectory() throws Exception {
		Path testDir = Paths.get("src/test/resources/scripts");
		ScriptMetadataLoader loader = createLoader(testDir, root.resolve("user"));
		List<ScriptMetadata> scripts = loader.loadScriptMetadata();
		Assertions.assertFalse(scripts.isEmpty());
	}


	@Test
	void returnsEmptyListForEmptyDirectory() throws Exception {
		Path emptyDir = Files.createTempDirectory("empty-scripts");
		ScriptMetadataLoader loader = createLoader(emptyDir, root.resolve("user"));
		List<ScriptMetadata> scripts = loader.loadScriptMetadata();
		assertTrue(scripts.isEmpty());
		Files.delete(emptyDir);
	}

	@Test
	void skipsMalformedJson() throws Exception {
		Path tempDir = Files.createTempDirectory("malformed-scripts");
		Path badJson = tempDir.resolve("bad.json");
		Path goodJson = tempDir.resolve("good.json");
		Files.writeString(badJson, "{ this is not valid json }");
		Files.writeString(goodJson, """
				{"filename": "good.py", "display_name": "Good", "parameters": [], "templates": ["csv"]}
				""");
		ScriptMetadataLoader loader = createLoader(tempDir, root.resolve("user"));
		List<ScriptMetadata> scripts = loader.loadScriptMetadata();
		assertEquals(1, scripts.size());
		assertEquals("good.py", scripts.getFirst().filename());
		Files.delete(badJson);
		Files.delete(goodJson);
		Files.delete(tempDir);
	}

	@Test
	void ignoresNonJsonFiles() throws Exception {
		Path tempDir = Files.createTempDirectory("mixed-scripts");
		Path txtFile = tempDir.resolve("not_a_script.txt");
		Files.writeString(txtFile, "not a script");
		ScriptMetadataLoader loader = createLoader(tempDir, root.resolve("user"));
		List<ScriptMetadata> scripts = loader.loadScriptMetadata();
		assertTrue(scripts.isEmpty());
		Files.delete(txtFile);
		Files.delete(tempDir);
	}

	@Test
	void listsBuiltInScriptsBeforeUserScripts() throws Exception {
		Path appDir = Files.createDirectories(root.resolve("app"));
		Path userDir = Files.createDirectories(root.resolve("user"));
		writeDescription(appDir, "built_in.py", "Built-in");
		writeDescription(userDir, "own.py", "Own");
		ScriptMetadataLoader loader = createLoader(appDir, userDir);

		List<ScriptMetadata> scripts = loader.loadScriptMetadata();

		assertEquals(List.of("built_in.py", "own.py"), List.of(scripts.get(0).filename(), scripts.get(1).filename()));
	}

	@Test
	void userScriptReplacesBuiltInScriptInPlace() throws Exception {
		Path appDir = Files.createDirectories(root.resolve("app"));
		Path userDir = Files.createDirectories(root.resolve("user"));
		writeDescription(appDir, "filter.py", "Built-in filter");
		writeDescription(userDir, "filter.py", "User filter");
		ScriptMetadataLoader loader = createLoader(appDir, userDir);

		List<ScriptMetadata> scripts = loader.loadScriptMetadata();

		assertEquals(1, scripts.size());
		assertEquals("User filter", scripts.getFirst().displayName());
	}

	@Test
	void skipsMissingFolders() throws Exception {
		Path appDir = Files.createDirectories(root.resolve("app"));
		Path userDir = root.resolve("user");
		writeDescription(appDir, "filter.py", "Filter");
		ScriptMetadataLoader withoutUserFolder = createLoader(appDir, userDir);
		// the user folder is created at start; removed later, it is skipped
		Files.delete(userDir);
		ScriptMetadataLoader withoutAppFolder = createLoader(root.resolve("missing"), appDir);

		assertEquals(1, withoutUserFolder.loadScriptMetadata().size());
		assertEquals(1, withoutAppFolder.loadScriptMetadata().size());
	}

	private static ScriptMetadataLoader createLoader(Path scriptsPath, Path userScriptsPath) {
		// values are kept in memory: settings are written to disk only on save
		Settings settings = new Settings();
		settings.setValue(PREF_USER_SCRIPTS, PREF_PATH, userScriptsPath);
		return new JsonScriptMetadataLoader(new ScriptPaths(new UserScriptsFolder(settings), scriptsPath));
	}

	private static void writeDescription(Path dir, String filename, String displayName) throws Exception {
		Files.writeString(dir.resolve(filename.replace(".py", ".json")), """
				{"filename": "%s", "display_name": "%s", "parameters": [], "templates": ["csv"]}
				""".formatted(filename, displayName));
	}
}
