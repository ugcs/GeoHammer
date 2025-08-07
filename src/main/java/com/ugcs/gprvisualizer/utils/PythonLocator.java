package com.ugcs.gprvisualizer.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class PythonLocator {
	private static final Logger log = LoggerFactory.getLogger(PythonLocator.class);
	private static final ExecutorService executor = Executors.newSingleThreadExecutor();

	public Future<String> getPythonExecutorPathAsync() {
		return executor.submit(PythonLocator::getPythonExecutorPath);
	}

	private static String getPythonExecutorPath() {
		String os = System.getProperty("os.name").toLowerCase();
		String[] command;
		if (os.contains("win")) {
			command = new String[]{"where", "python"};
		} else {
			command = new String[]{"which", "python3"};
		}
		try {
			Process process = new ProcessBuilder(command).start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			return reader.readLine();
		} catch (Exception e) {
			log.error("Error finding Python executable", e);
			return null;
		}
	}
}