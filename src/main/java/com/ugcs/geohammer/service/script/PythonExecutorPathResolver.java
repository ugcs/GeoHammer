package com.ugcs.geohammer.service.script;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.ugcs.geohammer.util.PythonLocator;
import org.springframework.stereotype.Service;

@Service
public class PythonExecutorPathResolver {

	private final PythonConfig pythonConfig;

	public PythonExecutorPathResolver(PythonConfig pythonConfig) {
		this.pythonConfig = pythonConfig;
	}

	public Path getPath(ExecutorService executor) throws InterruptedException {
		String pythonPath = pythonConfig.getPythonExecutorPath();
		if (pythonPath == null || pythonPath.isEmpty()) {
			try {
				Future<String> future = executor.submit(PythonLocator::getPythonExecutorPath);
				pythonPath = future.get();
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		return Path.of(pythonPath).getParent();
	}
}
