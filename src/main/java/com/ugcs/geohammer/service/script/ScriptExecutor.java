package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.ugcs.geohammer.Loader;
import com.ugcs.geohammer.analytics.EventSender;
import com.ugcs.geohammer.analytics.EventsFactory;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScriptExecutor {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutor.class);

	private final Loader loader;

	private final EventSender eventSender;

	private final EventsFactory eventsFactory;

	private final CommandExecutor commandExecutor;

	private final PythonService pythonService;

	private final ScriptPaths scriptPaths;

	// sgyFile -> scriptMetadata
	private final Map<SgyFile, ScriptMetadata> executingScripts = new ConcurrentHashMap<>();

	public ScriptExecutor(Loader loader,
						  EventSender eventSender,
						  EventsFactory eventsFactory,
						  CommandExecutor commandExecutor,
						  PythonService pythonService,
						  ScriptPaths scriptPaths) {
		this.loader = loader;
		this.eventSender = eventSender;
		this.eventsFactory = eventsFactory;
		this.commandExecutor = commandExecutor;
		this.pythonService = pythonService;
		this.scriptPaths = scriptPaths;
	}

	public FileSnapshot<?> executeScript(SgyFile sgyFile, ScriptMetadata scriptMetadata, Map<String, String> parameters,
							  Consumer<String> onScriptOutput)
			throws IOException, InterruptedException, DependencyImportException {
		return executeScript(sgyFile, scriptMetadata, parameters, onScriptOutput, false);
	}

	public FileSnapshot<?> executeScriptWithReinstall(SgyFile sgyFile, ScriptMetadata scriptMetadata,
							  Map<String, String> parameters, Consumer<String> onScriptOutput)
			throws IOException, InterruptedException, DependencyImportException {
		return executeScript(sgyFile, scriptMetadata, parameters, onScriptOutput, true);
	}

	private FileSnapshot<?> executeScript(SgyFile sgyFile, ScriptMetadata scriptMetadata, Map<String, String> parameters,
	                           Consumer<String> onScriptOutput, boolean forceReinstall)
			throws IOException, InterruptedException, DependencyImportException {
		Check.notNull(sgyFile);
		Check.notNull(scriptMetadata);

		// Check if script is already running for this file
		if (isExecuting(sgyFile)) {
			throw new RuntimeException("Script is already running for this file");
		}

		pythonService.checkVersion();

		executingScripts.put(sgyFile, scriptMetadata);
		File tempFile = null;
		try {
			File scriptFile = new File(scriptPaths.getScriptsPath().toFile(), scriptMetadata.filename());
			if (!scriptFile.exists()) {
				throw new IOException("Script file not found: " + scriptFile.getAbsolutePath());
			}

			if (forceReinstall) {
				pythonService.reinstallDependencies(scriptFile, onScriptOutput);
			} else {
				pythonService.installDependencies(scriptFile, onScriptOutput);
			}

			tempFile = copyToTempFile(sgyFile);

			List<String> command = buildCommand(
					scriptFile.toPath(),
					scriptMetadata,
					parameters,
					tempFile.toPath());

			eventSender.send(eventsFactory.createScriptExecutionStartedEvent(scriptMetadata.filename()));
			commandExecutor.executeCommand(command, onScriptOutput);
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException();
			}

			FileSnapshot<?> snapshot = createFileSnapshot(sgyFile);
			try {
				loader.loadFrom(sgyFile, tempFile);
			} catch (Exception e) {
				if (snapshot != null) {
					snapshot.discard();
				}
				throw e;
			}

			return snapshot;
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
		if (sgyFile instanceof TraceFile) {
			Files.copy(file.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return tempFile;
		}
		sgyFile.save(tempFile);
		return tempFile;
	}

	private List<String> buildCommand(Path scriptPath,
									  ScriptMetadata scriptMetadata,
									  Map<String, String> parameters,
									  Path tempFilePath) throws IOException {
		List<String> command = new ArrayList<>();

		String pythonPath = pythonService.getPythonPath().toString();
		command.add(pythonPath);

		command.add(scriptPath.toString());

		command.add(tempFilePath.toAbsolutePath().toString());

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

	private @Nullable FileSnapshot<?> createFileSnapshot(SgyFile sgyFile) {
		if (sgyFile instanceof TraceFile traceFile) {
			return traceFile.createSnapshotWithTraces();
		}
		if (sgyFile instanceof CsvFile csvFile) {
			return csvFile.createSnapshot();
		}
		return null;
	}

	public boolean isExecuting(@Nullable SgyFile sgyFile) {
		return sgyFile != null && executingScripts.containsKey(sgyFile);
	}

	@Nullable
	public ScriptMetadata getExecutingScriptMetadata(SgyFile sgyFile) {
		return executingScripts.get(sgyFile);
	}

	private boolean isBooleanParameter(ScriptMetadata metadata, String paramName) {
		return metadata.parameters().stream()
				.anyMatch(param -> param.name().equals(paramName) && param.type() == ScriptParameter.ParameterType.BOOLEAN);
	}
}
