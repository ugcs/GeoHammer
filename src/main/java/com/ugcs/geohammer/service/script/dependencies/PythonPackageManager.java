package com.ugcs.geohammer.service.script.dependencies;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.ugcs.geohammer.service.script.CommandExecutionException;
import com.ugcs.geohammer.service.script.ProcessCommandExecutor;
import com.ugcs.geohammer.service.script.PythonExecutorPathResolver;

public class PythonPackageManager {

	private final ExecutorService executorService;

	private final PythonExecutorPathResolver pythonExecutorPathResolver;

	private final ProcessCommandExecutor processCommandExecutor;

	public PythonPackageManager(ExecutorService executorService,
								PythonExecutorPathResolver pythonExecutorPathResolver,
								ProcessCommandExecutor processCommandExecutor) {
		this.executorService = executorService;
		this.pythonExecutorPathResolver = pythonExecutorPathResolver;
		this.processCommandExecutor = processCommandExecutor;

	}

	public boolean isInstalled(String packageName) throws InterruptedException, IOException {
		String pythonExecutorPath = pythonExecutorPathResolver.getPath(executorService).toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "show", packageName);

		try {
			processCommandExecutor.executeCommand(command, null);
			return true;
		} catch (CommandExecutionException e) {
			return false;
		}
	}

	public void install(String packageName, Consumer<String> onOutput) throws InterruptedException, IOException {
		String pythonExecutorPath = pythonExecutorPathResolver.getPath(executorService).toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "install", packageName);
		processCommandExecutor.executeCommand(command, onOutput);
	}
}
