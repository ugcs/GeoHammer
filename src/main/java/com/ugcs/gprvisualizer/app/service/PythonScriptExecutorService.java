package com.ugcs.gprvisualizer.app.service;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.ScriptExecutionView;
import com.ugcs.gprvisualizer.app.scripts.PythonConfig;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.utils.FileNames;
import com.ugcs.gprvisualizer.utils.PythonLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.Nullable;


@Service
public class PythonScriptExecutorService {

	private static final Logger log = LoggerFactory.getLogger(PythonScriptExecutorService.class);
	public static final String SCRIPTS_DIRECTORY = "scripts";
	private final ExecutorService executor = Executors.newCachedThreadPool();

	@Autowired
	private PythonConfig pythonConfig;

	private final Map<String, ScriptBinding> executingFiles = new ConcurrentHashMap<>();

	public Future<ScriptExecutionResult> executeScriptAsync(
			SgyFile selectedFile,
			ScriptExecutionView.ScriptMetadata scriptMetadata,
			Map<String, String> parameters) {
		File currentFile = selectedFile.getFile();
		if (currentFile == null || !currentFile.exists()) {
			return CompletableFuture.completedFuture(
					new ScriptExecutionResult.Error(-1, "Selected file does not exist: " + currentFile)
			);
		}

		String filename = currentFile.getName();
		String prefix = FileNames.removeExtension(filename);
		String suffix = "." + FileNames.getExtension(filename);
		try {
			File tempFile = Files.createTempFile(prefix, suffix).toFile();
			selectedFile.save(tempFile);

			SgyFile tempSgyFile = selectedFile.copy();
			tempSgyFile.setFile(tempFile);

			String fileKey = currentFile.getName();
			// Check if script is already running for this file
			if (executingFiles.containsKey(fileKey)) {
				return CompletableFuture.completedFuture(
						new ScriptExecutionResult.Error(-1, "Script is already running for this file")
				);
			}
			return executor.submit(() -> executeScript(fileKey, selectedFile, tempSgyFile, scriptMetadata, parameters));
		} catch (IOException e) {
			return CompletableFuture.completedFuture(
					new ScriptExecutionResult.Error(-1, "Script is already running for this file")
			);
		}
	}

	private ScriptExecutionResult executeScript(String fileKey, SgyFile originalSelectedFile, SgyFile tempSgyFile, ScriptExecutionView.ScriptMetadata scriptMetadata, Map<String, String> parameters) throws IOException, InterruptedException {
		executingFiles.put(fileKey, new ScriptBinding(scriptMetadata.filename(), tempSgyFile));
		try {
			String scriptFilename = scriptMetadata.filename();
			File file = tempSgyFile.getFile();
			if (file == null || !file.exists()) {
				return new ScriptExecutionResult.Error(-1, "Selected file does not exist: " + file);
			}
			Path path = file.toPath();
			List<String> command = buildCommand(scriptMetadata, parameters, path);

			log.info("Executing script: {}", String.join(" ", command));

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
			return new ScriptExecutionResult.Success(output.toString(), originalSelectedFile, path);
		} catch (URISyntaxException e) {
			return new ScriptExecutionResult.Error(-1, "Invalid script path: " + e.getMessage());
		} finally {
			executingFiles.remove(fileKey);
		}
	}

	/**
	 * Builds the process command:
	 * [python, <scripts/path>/<script.py>, <workingCopy>, --key value | --flag]
	 */
	private List<String> buildCommand(
			ScriptExecutionView.ScriptMetadata scriptMetadata,
			Map<String, String> parameters,
			Path filePath
	) throws URISyntaxException, IOException {
		List<String> command = new ArrayList<>();

		String pythonPath = pythonConfig.getPythonExecutorPath();
		if (pythonPath == null || pythonPath.isEmpty()) {
			try {
				pythonPath = new PythonLocator()
						.getPythonExecutorPathAsync()
						.get();
			} catch (Exception e) {
				throw new IOException("Failed to get Python executor: " + e.getMessage(), e);
			}
		}
		command.add(pythonPath);

		Path scriptsPath = getScriptsPath();

		command.add(scriptsPath.resolve(scriptMetadata.filename()).toString());

		command.add(filePath.toAbsolutePath().toString());

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

		return command;
	}

	public boolean isExecuting(SgyFile sgyFile) {
		File file = sgyFile.getFile();
		if (file == null || !file.exists()) {
			return false;
		}
		return executingFiles.containsKey(file.getName());
	}

	public Path getScriptsPath() throws URISyntaxException {
		// Try project root first (for IDE/dev)
		Path projectRoot = Paths.get(System.getProperty("user.dir"));
		Path scriptsInRoot = projectRoot.resolve(SCRIPTS_DIRECTORY);
		if (scriptsInRoot.toFile().exists()) {
			return scriptsInRoot;
		}

		// Fallback to target/scripts (for packaged app)
		Path currentDir = Paths.get(FileTemplates.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
		return currentDir.resolve(SCRIPTS_DIRECTORY);
	}

	@Nullable
	public String getExecutingScriptName(SgyFile sgyFile) {
		File file = sgyFile.getFile();
		if (file == null || !file.exists()) {
			return null;
		}
		ScriptBinding binding = executingFiles.get(file.getName());
		if (binding != null) {
			return binding.scriptFilename();
		}
		return null;
	}

	private boolean isBooleanParameter(ScriptExecutionView.ScriptMetadata metadata, String paramName) {
		return metadata.parameters().stream()
				.anyMatch(param -> param.name().equals(paramName) && param.type() == ScriptExecutionView.PythonScriptParameter.ParameterType.BOOLEAN);
	}

	public static sealed abstract class ScriptExecutionResult permits ScriptExecutionResult.Success, ScriptExecutionResult.Error {

		@Nullable
		private final String output;

		private ScriptExecutionResult(@Nullable String output) {
			this.output = output;
		}

		@Nullable
		public String getOutput() {
			return output;
		}

		public static final class Success extends ScriptExecutionResult {
			private final SgyFile originalSelectedFile;
			private final Path modifiedFilePath;

			public Success(String output, SgyFile originalSelectedFile, Path modifiedFilePath) {
				super(output);
				this.originalSelectedFile = originalSelectedFile;
				this.modifiedFilePath = modifiedFilePath;
			}

			public SgyFile getOriginalSelectedFile() {
				return originalSelectedFile;
			}

			public Path getModifiedFilePath() {
				return modifiedFilePath;
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

	record ScriptBinding(String scriptFilename, SgyFile file) {}
}
