package com.ugcs.geohammer.service.script.dependecies;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.ugcs.geohammer.service.script.ProcessCommandExecutor;
import com.ugcs.geohammer.service.script.PythonExecutorPathResolver;

public class PythonPackageManager {

	private final PythonExecutorPathResolver pythonExecutorPathResolver;

	private final ExecutorService executorService;

	public PythonPackageManager(PythonExecutorPathResolver pythonExecutorPathResolver, ExecutorService executorService) {
		this.pythonExecutorPathResolver = pythonExecutorPathResolver;
		this.executorService = executorService;
	}

	public boolean isInstalled(String packageName) throws InterruptedException, IOException {
		String pythonExecutorPath = pythonExecutorPathResolver.getPath(executorService).toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "show", packageName);
		Process process = new ProcessBuilder(command).start();
		try {
			int exitCode = process.waitFor();
			return exitCode == 0;
		} finally {
			process.destroy();
		}
	}

	public void install(String packageName, Consumer<String> onOutput) throws InterruptedException, IOException {
		String pythonExecutorPath = pythonExecutorPathResolver.getPath(executorService).toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "install", packageName);
		ProcessCommandExecutor.executeCommand(command, onOutput);
	}
}
