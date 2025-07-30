package com.ugcs.gprvisualizer.app.service;

import com.ugcs.gprvisualizer.app.PythonScriptsView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class PythonScriptExecutorService {

	private static final Logger log = LoggerFactory.getLogger(PythonScriptExecutorService.class);
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	public Future<ScriptExecutionResult> executeScriptAsync(
			PythonScriptsView.PythonScriptMetadata scriptMetadata,
			Map<String, String> parameters) {
		return executor.submit(() -> executeScript(scriptMetadata, parameters));
	}
	private ScriptExecutionResult executeScript(PythonScriptsView.PythonScriptMetadata scriptMetadata, Map<String, String> parameters) throws IOException, InterruptedException {
		String scriptFilename = scriptMetadata.filename();
		List<String> command = new ArrayList<>();
		command.add("python3");
		command.add("scripts/" + scriptFilename);

		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (isBooleanParameter(scriptMetadata, key)) {
				if (Boolean.parseBoolean(value)) {
					command.add("--" + key);
				}
			} else {
				command.add("--" + key);
				command.add(value);
			}
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);

		Process process = pb.start();

		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append(System.lineSeparator());
			}
		}

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			log.error("Script {} execution failed with exit code {}: {}", scriptFilename, exitCode, output);
			return new ScriptExecutionResult.Error(exitCode, output.toString());
		}
		return new ScriptExecutionResult.Success(output.toString());
	}

	private boolean isBooleanParameter(PythonScriptsView.PythonScriptMetadata metadata, String paramName) {
		return metadata.parameters().stream()
				.anyMatch(param -> param.name().equals(paramName) && param.type() == PythonScriptsView.PythonScriptParameter.ParameterType.BOOLEAN);
	}

	public static sealed abstract class ScriptExecutionResult permits ScriptExecutionResult.Success, ScriptExecutionResult.Error {
		private final String output;
		private ScriptExecutionResult(String output) {
			this.output = output;
		}

		public String getOutput() {
			return output;
		}

		public static final class Success extends ScriptExecutionResult {
			public Success(String output) {
				super(output);
			}
		}

		public static final class Error extends ScriptExecutionResult {
			private final int code;

			public Error(int code, String output) {
				super(output);
				this.code = code;
			}

			public int getCode() {
				return code;
			}
		}
	}
}
