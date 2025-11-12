package com.ugcs.geohammer.service.script;

import com.ugcs.geohammer.service.script.JsonScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class JsonScriptMetadataLoaderTest {
	@Test
	void loadsScriptsFromDirectory() throws Exception {
		Path testDir = Paths.get("src/test/resources/scripts");
		ScriptMetadataLoader loader = new JsonScriptMetadataLoader();
		List<ScriptMetadata> scripts = loader.loadScriptMetadata(testDir);
		Assertions.assertFalse(scripts.isEmpty());
	}


	@Test
	void returnsEmptyListForEmptyDirectory() throws Exception {
		Path emptyDir = Files.createTempDirectory("empty-scripts");
		ScriptMetadataLoader loader = new JsonScriptMetadataLoader();
		List<ScriptMetadata> scripts = loader.loadScriptMetadata(emptyDir);
		assertTrue(scripts.isEmpty());
		Files.delete(emptyDir);
	}

	@Test
	void throwsExceptionOnMalformedJson() throws Exception {
		Path tempDir = Files.createTempDirectory("malformed-scripts");
		Path badJson = tempDir.resolve("bad.json");
		Files.writeString(badJson, "{ this is not valid json }");
		ScriptMetadataLoader loader = new JsonScriptMetadataLoader();
		assertThrows(IOException.class, () -> loader.loadScriptMetadata(tempDir));
		Files.delete(badJson);
		Files.delete(tempDir);
	}

	@Test
	void ignoresNonJsonFiles() throws Exception {
		Path tempDir = Files.createTempDirectory("mixed-scripts");
		Path txtFile = tempDir.resolve("not_a_script.txt");
		Files.writeString(txtFile, "not a script");
		ScriptMetadataLoader loader = new JsonScriptMetadataLoader();
		List<ScriptMetadata> scripts = loader.loadScriptMetadata(tempDir);
		assertTrue(scripts.isEmpty());
		Files.delete(txtFile);
		Files.delete(tempDir);
	}
}
