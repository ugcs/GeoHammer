package com.ugcs.geohammer.service.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PackageInstaller {

	private static final Logger log = LoggerFactory.getLogger(PackageInstaller.class);

	private final CommandExecutor commandExecutor;

	private final PythonInterpreter interpreter;

	public PackageInstaller(CommandExecutor commandExecutor, PythonInterpreter interpreter) {
		this.commandExecutor = commandExecutor;
		this.interpreter = interpreter;
	}

	public boolean isInstalled(String packageName) throws InterruptedException, IOException {
		List<String> command = List.of(interpreter.getPath().toString(), "-m", "pip", "show", packageName);

		try {
			commandExecutor.executeCommand(command, null);
			return true;
		} catch (CommandExecutionException e) {
			return false;
		}
	}

	public void install(String packageName, Consumer<String> onOutput) throws InterruptedException, IOException {
		List<String> command = List.of(
				interpreter.getPath().toString(), "-m", "pip", "install", packageName);
		commandExecutor.executeCommand(command, onOutput);
	}

	public boolean installFromRequirements(Path requirementsPath, Consumer<String> onOutput)
			throws IOException, InterruptedException {
		if (!hasEntries(requirementsPath)) {
			return false;
		}

		List<String> command = List.of(
				interpreter.getPath().toString(),
				"-m", "pip", "install",
				"-r", requirementsPath.toString()
		);
		commandExecutor.executeCommand(command, onOutput);
		return true;
	}

	public boolean reinstallFromRequirements(Path requirementsPath, Consumer<String> onOutput)
			throws IOException, InterruptedException {
		if (!hasEntries(requirementsPath)) {
			return false;
		}

		List<String> command = List.of(
				interpreter.getPath().toString(),
				"-m", "pip", "install",
				"--force-reinstall",
				"--no-cache-dir",
				"-r", requirementsPath.toString()
		);
		commandExecutor.executeCommand(command, onOutput);
		return true;
	}

	private static boolean hasEntries(Path requirementsPath) throws IOException {
		if (!Files.exists(requirementsPath)) {
			log.warn("No requirements file at {}, skipping dependency installation.", requirementsPath);
			return false;
		}
		return !Files.readString(requirementsPath).isBlank();
	}
}
