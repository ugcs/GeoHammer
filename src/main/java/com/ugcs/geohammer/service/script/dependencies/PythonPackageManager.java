package com.ugcs.geohammer.service.script.dependencies;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.ugcs.geohammer.service.script.CommandExecutionException;
import com.ugcs.geohammer.service.script.CommandExecutor;
import com.ugcs.geohammer.service.script.PythonExecutorPathResolver;

public class PythonPackageManager {

	private final ExecutorService executorService;

	private final PythonExecutorPathResolver pythonPathResolver;

	private final CommandExecutor commandExecutor;

	public PythonPackageManager(ExecutorService executorService,
								PythonExecutorPathResolver pythonPathResolver,
								CommandExecutor commandExecutor) {
		this.executorService = executorService;
		this.pythonPathResolver = pythonPathResolver;
		this.commandExecutor = commandExecutor;

	}

	public boolean isInstalled(String packageName) throws InterruptedException, IOException {
		String pythonExecutorPath = pythonPathResolver.getPythonExecutablePath(executorService).toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "show", packageName);

		try {
			commandExecutor.executeCommand(command, null);
			return true;
		} catch (CommandExecutionException e) {
			return false;
		}
	}

	public void install(String packageName, Consumer<String> onOutput) throws InterruptedException, IOException {
		String pythonExecutorPath = pythonPathResolver.getPythonExecutablePath(executorService).toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "install", packageName);
		commandExecutor.executeCommand(command, onOutput);
	}
}
