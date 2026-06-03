package com.ugcs.geohammer.service.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckImportsTest {

	private static final Path CHECK_IMPORTS_SCRIPT = Path.of("scripts", "check_imports.py");

	private static final String MISSING_PREFIX = "MISSING:";

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
		ProcessResult result = runCheckImports(createScript("import os\n"));

		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.isBlank());
	}

	@Test
	void stdlibFromImport_succeeds() throws Exception {
		ProcessResult result = runCheckImports(createScript("from os.path import join\n"));

		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.isBlank());
	}

	@Test
	void nonexistentImport_reportsMissingPrefix() throws Exception {
		ProcessResult result = runCheckImports(createScript("import nonexistent_module_xyz\n"));

		assertEquals(1, result.exitCode);
		assertEquals(MISSING_PREFIX + "nonexistent_module_xyz", result.stdout.trim());
	}

	@Test
	void nonexistentFromImport_reportsMissingPrefix() throws Exception {
		ProcessResult result = runCheckImports(createScript("from nonexistent_abc_pkg import something\n"));

		assertEquals(1, result.exitCode);
		assertEquals(MISSING_PREFIX + "nonexistent_abc_pkg", result.stdout.trim());
	}

	@Test
	void dottedImport_reportsTopLevelPackage() throws Exception {
		ProcessResult result = runCheckImports(createScript("import nonexistent_pkg.sub.module\n"));

		assertEquals(1, result.exitCode);
		assertEquals(MISSING_PREFIX + "nonexistent_pkg", result.stdout.trim());
	}

	@Test
	void multipleMissingImports_reportsFirstOnly() throws Exception {
		ProcessResult result = runCheckImports(createScript(
				"""
						import nonexistent_first
						import nonexistent_second
						"""));

		assertEquals(1, result.exitCode);
		assertEquals(MISSING_PREFIX + "nonexistent_first", result.stdout.trim());
	}

	@Test
	void relativeImport_isIgnored() throws Exception {
		ProcessResult result = runCheckImports(createScript("from . import nothing\n"));

		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.isBlank());
	}

	@Test
	void utf8ScriptWithNonAsciiChars_doesNotCrash() throws Exception {
		String source = """
				# Comment with non-ASCII: cyrillic, arrow ->, em-dash —
				import os
				""";
		Path scriptPath = tempDir.resolve("utf8_script.py");
		Files.writeString(scriptPath, source, StandardCharsets.UTF_8);

		ProcessResult result = runCheckImports(scriptPath.toFile());

		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.isBlank());
		assertTrue(result.stderr.isBlank(), "stderr should be empty, got: " + result.stderr);
	}

	@Test
	void utf8ScriptWithNonAsciiChars_stillDetectsMissingModule() throws Exception {
		String source = "# Кириллический комментарий\nimport nonexistent_utf8_xyz\n";
		Path scriptPath = tempDir.resolve("utf8_missing.py");
		Files.writeString(scriptPath, source, StandardCharsets.UTF_8);

		ProcessResult result = runCheckImports(scriptPath.toFile());

		assertEquals(1, result.exitCode);
		assertEquals(MISSING_PREFIX + "nonexistent_utf8_xyz", result.stdout.trim());
	}

	@Test
	void syntaxErrorInTarget_doesNotEmitMissingLine() throws Exception {
		ProcessResult result = runCheckImports(createScript("def broken(\n"));

		assertNotEquals(0, result.exitCode);
		assertFalse(result.stdout.contains(MISSING_PREFIX),
				"stdout must not contain MISSING: when the helper crashes, got: " + result.stdout);
		assertTrue(result.stderr.contains("SyntaxError"),
				"stderr should carry the Python traceback, got: " + result.stderr);
	}

	@Test
	void nonexistentTargetFile_doesNotEmitMissingLine() throws Exception {
		File missing = new File(tempDir.toFile(), "no_such_file.py");

		ProcessResult result = runCheckImports(missing);

		assertNotEquals(0, result.exitCode);
		assertFalse(result.stdout.contains(MISSING_PREFIX),
				"stdout must not contain MISSING: when the helper crashes, got: " + result.stdout);
		assertTrue(result.stderr.contains("FileNotFoundError")
						|| result.stderr.contains("No such file"),
				"stderr should carry the file-not-found traceback, got: " + result.stderr);
	}

	private File createScript(String content) throws Exception {
		Path scriptPath = tempDir.resolve("test_script.py");
		Files.writeString(scriptPath, content, StandardCharsets.UTF_8);
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

		String stdout = drain(process.getInputStream());
		String stderr = drain(process.getErrorStream());
		int exitCode = process.waitFor();
		return new ProcessResult(exitCode, stdout, stderr);
	}

	private static String drain(InputStream stream) throws Exception {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			return reader.lines().collect(Collectors.joining("\n"));
		}
	}

	private record ProcessResult(int exitCode, String stdout, String stderr) {
	}
}
