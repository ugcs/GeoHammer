package com.ugcs.geohammer.service.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

import com.ugcs.geohammer.model.PathSettings;
import com.ugcs.geohammer.util.OperatingSystemUtils;
import org.springframework.stereotype.Component;

@Component
public class PythonInterpreter {

	private static final PythonVersion MINIMAL_VERSION = PythonVersion.parse("3.8");

	private static final String INSTALL_HINT =
			"Please install Python v" + MINIMAL_VERSION
					+ " or higher. Download: https://www.python.org/downloads/";

	private final CommandExecutor commandExecutor;

	private final PathSettings pathSettings;

	public PythonInterpreter(CommandExecutor commandExecutor, PathSettings pathSettings) {
		this.commandExecutor = commandExecutor;
		this.pathSettings = pathSettings;
	}

	public Path getPath() throws IOException {
		String pythonPath = pathSettings.getPythonExecutable();
		if (pythonPath == null || pythonPath.isEmpty()) {
			String[] command;
			if (OperatingSystemUtils.isWindows()) {
				command = new String[]{"where", "python"};
			} else {
				command = new String[]{"which", "python3"};
			}
			Process process = new ProcessBuilder(command).start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				pythonPath = reader.readLine();
			}
		}
		if (pythonPath == null || pythonPath.isEmpty()) {
			throw new IllegalStateException("Python executable not found. Please install Python or configure path in settings.");
		}
		return Path.of(pythonPath);
	}

	public void checkVersion() throws InterruptedException {
		try {
			PythonVersion pythonVersion = getVersion();
			if (pythonVersion.compareTo(MINIMAL_VERSION) < 0) {
				throw new IllegalStateException(
						"Python version " + pythonVersion + " is not supported. " + INSTALL_HINT);
			}
		} catch (CommandExecutionException | IOException e) {
			throw new IllegalStateException(
					"Python isn't installed on your PC. " + INSTALL_HINT, e);
		}
	}

	public PythonVersion getVersion() throws IOException, InterruptedException {
		StringBuilder output = new StringBuilder();
		commandExecutor.executeCommand(
				List.of(getPath().toString(), "--version"),
				line -> output.append(line).append('\n'));
		return PythonVersion.parse(output.toString().trim());
	}
}
