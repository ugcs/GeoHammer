package com.ugcs.gprvisualizer.app.scripts;

import com.ugcs.gprvisualizer.app.PythonScriptsView;
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
		PythonScriptMetadataLoader loader = new JsonScriptMetadataMetadataLoader();
		List<PythonScriptsView.PythonScriptMetadata> scripts = loader.loadScriptsMetadata(testDir);
		Assertions.assertFalse(scripts.isEmpty());
	}


	@Test
	void returnsEmptyListForEmptyDirectory() throws Exception {
		Path emptyDir = Files.createTempDirectory("empty-scripts");
		PythonScriptMetadataLoader loader = new JsonScriptMetadataMetadataLoader();
		List<PythonScriptsView.PythonScriptMetadata> scripts = loader.loadScriptsMetadata(emptyDir);
		assertTrue(scripts.isEmpty());
		Files.delete(emptyDir);
	}

	@Test
	void throwsExceptionOnMalformedJson() throws Exception {
		Path tempDir = Files.createTempDirectory("malformed-scripts");
		Path badJson = tempDir.resolve("bad.json");
		Files.writeString(badJson, "{ this is not valid json }");
		PythonScriptMetadataLoader loader = new JsonScriptMetadataMetadataLoader();
		assertThrows(IOException.class, () -> loader.loadScriptsMetadata(tempDir));
		Files.delete(badJson);
		Files.delete(tempDir);
	}

	@Test
	void ignoresNonJsonFiles() throws Exception {
		Path tempDir = Files.createTempDirectory("mixed-scripts");
		Path txtFile = tempDir.resolve("not_a_script.txt");
		Files.writeString(txtFile, "not a script");
		PythonScriptMetadataLoader loader = new JsonScriptMetadataMetadataLoader();
		List<PythonScriptsView.PythonScriptMetadata> scripts = loader.loadScriptsMetadata(tempDir);
		assertTrue(scripts.isEmpty());
		Files.delete(txtFile);
		Files.delete(tempDir);
	}
}
