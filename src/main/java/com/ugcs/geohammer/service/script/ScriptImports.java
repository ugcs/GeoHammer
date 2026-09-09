package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;


@Component
public class ScriptImports {

	private static final String MISSING_IMPORTS_PREFIX = "MISSING:";

	private final CommandExecutor commandExecutor;

	private final PythonInterpreter interpreter;

	private final ScriptPaths scriptPaths;

	public ScriptImports(CommandExecutor commandExecutor, PythonInterpreter interpreter, ScriptPaths scriptPaths) {
		this.commandExecutor = commandExecutor;
		this.interpreter = interpreter;
		this.scriptPaths = scriptPaths;
	}

	public void verify(File scriptFile) throws IOException, InterruptedException, DependencyImportException {
		File checkImportsScript = scriptPaths.getCheckImportsScript().toFile();
		if (!checkImportsScript.exists()) {
			throw new IllegalStateException("Import check script not found: " + checkImportsScript.getAbsolutePath());
		}
		List<String> command = List.of(
				interpreter.getPath().toString(),
				checkImportsScript.getAbsolutePath(),
				scriptFile.getAbsolutePath()
		);
		AtomicReference<String> missingModule = new AtomicReference<>();
		StringBuilder outputBuffer = new StringBuilder();
		try {
			commandExecutor.executeCommand(command, line -> {
				if (!outputBuffer.isEmpty()) {
					outputBuffer.append(System.lineSeparator());
				}
				outputBuffer.append(line);
				if (line.startsWith(MISSING_IMPORTS_PREFIX)) {
					String name = line.substring(MISSING_IMPORTS_PREFIX.length()).trim();
					if (!name.isEmpty()) {
						missingModule.compareAndSet(null, name);
					}
				}
			});
		} catch (CommandExecutionException e) {
			String missing = missingModule.get();
			if (missing != null) {
				throw new DependencyImportException(missing);
			}
			String output = outputBuffer.toString().trim();
			String message = output.isEmpty()
					? "Import check script failed (exit code " + e.getExitCode() + ")"
					: "Import check script failed:" + System.lineSeparator() + output;
			throw new IllegalStateException(message);
		}
	}
}
