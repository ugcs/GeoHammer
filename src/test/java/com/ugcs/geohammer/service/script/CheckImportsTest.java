package com.ugcs.geohammer.service.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckImportsTest {

	private static final Path CHECK_IMPORTS_SCRIPT = Path.of("scripts", "check_imports.py");

	@TempDir
	Path tempDir;

	@BeforeAll
	static void checkPythonAvailable() throws Exception {
		ProcessBuilder pb = new ProcessBuilder("python3", "--version");
		pb.redirectErrorStream(true);
		Process process = pb.start();
		int exitCode = process.waitFor();
		if (exitCode != 0) {
			throw new IllegalStateException("Python 3 is not available in PATH");
		}
	}

	@Test
	void stdlibImport_succeeds() throws Exception {
		File script = createScript("import os\n");
		ProcessResult result = runCheckImports(script);

		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.isBlank());
	}

	@Test
	void stdlibFromImport_succeeds() throws Exception {
		File script = createScript("from os.path import join\n");
		ProcessResult result = runCheckImports(script);

		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.isBlank());
	}

	@Test
	void nonexistentModule_failsWithModuleName() throws Exception {
		File script = createScript("import nonexistent_module_xyz\n");
		ProcessResult result = runCheckImports(script);

		assertNotEquals(0, result.exitCode);
		assertEquals("nonexistent_module_xyz", result.stdout.trim());
	}

	@Test
	void nonexistentFromImport_failsWithModuleName() throws Exception {
		File script = createScript("from nonexistent_abc_pkg import something\n");
		ProcessResult result = runCheckImports(script);

		assertNotEquals(0, result.exitCode);
		assertEquals("nonexistent_abc_pkg", result.stdout.trim());
	}

	private File createScript(String content) throws Exception {
		Path scriptPath = tempDir.resolve("test_script.py");
		Files.writeString(scriptPath, content);
		return scriptPath.toFile();
	}

	private ProcessResult runCheckImports(File targetScript) throws Exception {
		File checkScript = CHECK_IMPORTS_SCRIPT.toFile();
		assertTrue(checkScript.exists(), "check_imports.py must exist at " + checkScript.getAbsolutePath());

		ProcessBuilder pb = new ProcessBuilder(
				"python3",
				checkScript.getAbsolutePath(),
				targetScript.getAbsolutePath()
		);
		pb.redirectErrorStream(false);
		Process process = pb.start();

		String stdout;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			stdout = reader.lines().reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
		}

		int exitCode = process.waitFor();
		return new ProcessResult(exitCode, stdout);
	}

	private record ProcessResult(int exitCode, String stdout) {
	}
}
