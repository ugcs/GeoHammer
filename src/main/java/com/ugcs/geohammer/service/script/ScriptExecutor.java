package com.ugcs.geohammer.service.script;

import com.ugcs.geohammer.analytics.EventSender;
import com.ugcs.geohammer.analytics.EventsFactory;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.Loader;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.PythonLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.annotation.Nullable;

@Service
public class ScriptExecutor {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutor.class);

	public static final String SCRIPTS_DIRECTORY = "scripts";

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private final PythonConfig pythonConfig;

	private final Loader loader;

	private final EventSender eventSender;

	private final EventsFactory eventsFactory;

	private final PythonDependenciesInstaller pythonDependenciesInstaller;

	// sgyFile -> scriptName
	private final Map<SgyFile, String> executingScripts = new ConcurrentHashMap<>();

	public ScriptExecutor(PythonConfig pythonConfig, Loader loader, EventSender eventSender, EventsFactory eventsFactory, PythonDependenciesInstaller pythonDependenciesInstaller) {
		this.pythonConfig = pythonConfig;
		this.loader = loader;
		this.eventSender = eventSender;
		this.eventsFactory = eventsFactory;
		this.pythonDependenciesInstaller = pythonDependenciesInstaller;
	}

	public void executeScript(SgyFile sgyFile, ScriptMetadata scriptMetadata, Map<String, String> parameters,
			Consumer<String> onScriptOutput) throws IOException, InterruptedException {
		Check.notNull(sgyFile);
		Check.notNull(scriptMetadata);

		// Check if script is already running for this file
		if (isExecuting(sgyFile)) {
			throw new RuntimeException("Script is already running for this file");
		}

		executingScripts.put(sgyFile, scriptMetadata.filename());
		File tempFile = null;
		try {
			tempFile = copyToTempFile(sgyFile);
			File scriptFile = new File(getScriptsPath().toFile(), scriptMetadata.filename());
			if (!scriptFile.exists()) {
				throw new IOException("Script file not found: " + scriptFile.getAbsolutePath());
			}

			pythonDependenciesInstaller.installIfNeeded(scriptFile, scriptMetadata, executor, onScriptOutput);

			List<String> command = buildCommand(scriptFile.toPath(), scriptMetadata, parameters, tempFile.toPath());
			eventSender.send(eventsFactory.createScriptExecutionStartedEvent(scriptMetadata.filename()));
			runScript(command, onScriptOutput);
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException();
			}

			loader.loadFrom(sgyFile, tempFile);
		} finally {
			executingScripts.remove(sgyFile);
			if (tempFile != null) {
				if (!tempFile.delete()) {
					log.warn("Failed to delete temporary file: {}", tempFile);
				}
			}
		}
	}

	private File copyToTempFile(SgyFile sgyFile) throws IOException {
		Check.notNull(sgyFile);

		File file = Check.notNull(sgyFile.getFile());
		String fileName = file.getName();
		String prefix = FileNames.removeExtension(fileName);
		String suffix = "." + FileNames.getExtension(fileName);

		File tempFile = Files.createTempFile(prefix, suffix).toFile();
		sgyFile.save(tempFile);
		return tempFile;
	}

	private void runScript(List<String> command, Consumer<String> onOutput) throws IOException, InterruptedException {
		log.info("Executing script: {}", String.join(" ", command));

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(true);

		Process process = pb.start();

		if (onOutput != null) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					onOutput.accept(line);
				}
			}
		}

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			log.error("Process failed with exit code {}", exitCode);
			throw new ScriptException(exitCode);
		}
	}

	/**
	 * Builds the process command:
	 * [python, <scripts/path>/<script.py>, <workingCopy>, --key value | --flag]
	 */
	private List<String> buildCommand(Path scriptPath, ScriptMetadata scriptMetadata, Map<String, String> parameters, Path filePath)
            throws InterruptedException {
		List<String> command = new ArrayList<>();

		String pythonPath = pythonConfig.getPythonExecutorPath();
		if (pythonPath == null || pythonPath.isEmpty()) {
			try {
				Future<String> future = executor.submit(PythonLocator::getPythonExecutorPath);
				pythonPath = future.get();
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		command.add(pythonPath);

		command.add(scriptPath.toString());

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

	public boolean isExecuting(@Nullable SgyFile sgyFile) {
		return sgyFile != null && executingScripts.containsKey(sgyFile);
	}

	@Nullable
	public String getExecutingScriptName(SgyFile sgyFile) {
		return executingScripts.get(sgyFile);
	}

	public Path getScriptsPath() {
		// Try project root first (for IDE/dev)
		Path projectRoot = Paths.get(System.getProperty("user.dir"));
		Path scriptsInRoot = projectRoot.resolve(SCRIPTS_DIRECTORY);
		if (scriptsInRoot.toFile().exists()) {
			return scriptsInRoot;
		}

		// Fallback to target/scripts (for packaged app)
		URI codeSource;
		try {
            codeSource = FileTemplates.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        Path currentDir = Paths.get(codeSource).getParent();
		return currentDir.resolve(SCRIPTS_DIRECTORY);
	}

	private boolean isBooleanParameter(ScriptMetadata metadata, String paramName) {
		return metadata.parameters().stream()
				.anyMatch(param -> param.name().equals(paramName) && param.type() == ScriptParameter.ParameterType.BOOLEAN);
	}
}
