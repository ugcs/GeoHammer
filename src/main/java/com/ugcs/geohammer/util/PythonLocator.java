package com.ugcs.geohammer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class PythonLocator {

	public static String getPythonExecutorPath() throws IOException {
		String os = System.getProperty("os.name").toLowerCase();
		String[] command;
		if (os.contains("win")) {
			command = new String[]{"where", "python"};
		} else {
			command = new String[]{"which", "python3"};
		}
		Process process = new ProcessBuilder(command).start();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			return reader.readLine();
		}
	}
}