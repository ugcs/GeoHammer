package com.ugcs.geohammer.service.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScriptRequirementsTest {

	@TempDir
	Path scriptDirectory;

	@Test
	void excludesSiblingModule() throws Exception {
		Files.createFile(scriptDirectory.resolve("script_utils.py"));

		List<String> kept = ScriptRequirements.excludeLocalModules(
				List.of("numpy", "pandas", "script_utils"), scriptDirectory);

		assertEquals(List.of("numpy", "pandas"), kept);
	}

	@Test
	void comparesNormalizedNames() throws Exception {
		Files.createFile(scriptDirectory.resolve("script_utils.py"));

		List<String> kept = ScriptRequirements.excludeLocalModules(
				List.of("Script-Utils", "script.utils", "pandas"), scriptDirectory);

		assertEquals(List.of("pandas"), kept);
	}

	@Test
	void excludesPackageDirectoryOnly() throws Exception {
		Path helpers = Files.createDirectory(scriptDirectory.resolve("helpers"));
		Files.createFile(helpers.resolve("__init__.py"));
		Files.createDirectory(scriptDirectory.resolve("data"));

		List<String> kept = ScriptRequirements.excludeLocalModules(
				List.of("helpers", "data"), scriptDirectory);

		assertEquals(List.of("data"), kept);
	}

	@Test
	void ignoresNonPythonFiles() throws Exception {
		Files.createFile(scriptDirectory.resolve("scipy.json"));

		List<String> kept = ScriptRequirements.excludeLocalModules(List.of("scipy"), scriptDirectory);

		assertEquals(List.of("scipy"), kept);
	}

	@Test
	void handlesSpecifiersAndComments() throws Exception {
		Files.createFile(scriptDirectory.resolve("script_utils.py"));

		List<String> kept = ScriptRequirements.excludeLocalModules(
				List.of("# generated", "", "script_utils==1.0", "script-utils[extra]>=2", "pandas>=2.0"),
				scriptDirectory);

		assertEquals(List.of("# generated", "", "pandas>=2.0"), kept);
	}
}
