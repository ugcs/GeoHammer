package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.ugcs.geohammer.Loader;
import com.ugcs.geohammer.analytics.EventSender;
import com.ugcs.geohammer.analytics.EventsFactory;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.FileNames;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScriptExecutor {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutor.class);

	private static final String PYTHONPATH_VARIABLE = "PYTHONPATH";

	private final Loader loader;

	private final EventSender eventSender;

	private final EventsFactory eventsFactory;

	private final CommandExecutor commandExecutor;

	private final PythonInterpreter pythonInterpreter;

	private final ScriptPaths scriptPaths;

	public ScriptExecutor(Loader loader,
	                      EventSender eventSender,
	                      EventsFactory eventsFactory,
	                      CommandExecutor commandExecutor,
	                      PythonInterpreter pythonInterpreter,
	                      ScriptPaths scriptPaths) {
		this.loader = loader;
		this.eventSender = eventSender;
		this.eventsFactory = eventsFactory;
		this.commandExecutor = commandExecutor;
		this.pythonInterpreter = pythonInterpreter;
		this.scriptPaths = scriptPaths;
	}

	public void execute(SgyFile sgyFile, File scriptFile, ScriptMetadata metadata, Map<String, String> params,
	                    Consumer<String> output, Consumer<FileSnapshot<?>> onSnapshotCreated)
			throws IOException, InterruptedException, ScriptValidationException {
		Check.notNull(sgyFile);
		Check.notNull(scriptFile);
		Check.notNull(metadata);

		File tempFile = createTempFile(sgyFile);
		try {
			IndexRange lineRange = resolveLineRange(sgyFile, metadata, params);
			copy(sgyFile, tempFile, lineRange);

			Map<String, String> fileParams = resolveFileParams(sgyFile, metadata, params);
			List<String> command = buildCommand(scriptFile, metadata, fileParams, tempFile);
			eventSender.send(eventsFactory.createScriptExecutionStartedEvent(
					metadata.filename(), pythonInterpreter.getVersion().toString()));
			String pythonImportFolders = createPythonImportFolders(scriptPaths.getScriptsPath(),
					System.getenv(PYTHONPATH_VARIABLE));
			commandExecutor.executeCommand(command, null, Map.of(PYTHONPATH_VARIABLE, pythonImportFolders), output);
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException();
			}

			if (metadata.replacesInput()) {
				applyResult(sgyFile, tempFile, onSnapshotCreated);
			}
		} finally {
			if (!tempFile.delete()) {
				log.warn("Failed to delete temporary file: {}", tempFile);
			}
		}
	}

	private static String createPythonImportFolders(Path scriptsPath, @Nullable String existingPythonFolders) {
		String pythonImportFolders = scriptsPath.toAbsolutePath().toString();
		if (!Strings.isNullOrEmpty(existingPythonFolders)) {
			pythonImportFolders += File.pathSeparator + existingPythonFolders;
		}
		return pythonImportFolders;
	}

	private File createTempFile(SgyFile sgyFile) throws IOException {
		File source = Check.notNull(sgyFile.getFile());
		String fileName = source.getName();
		String prefix = FileNames.removeExtension(fileName);
		String suffix = "." + FileNames.getExtension(fileName);
		return Files.createTempFile(prefix, suffix).toFile();
	}

	@Nullable
	private IndexRange resolveLineRange(SgyFile sgyFile, ScriptMetadata metadata, Map<String, String> params)
			throws ScriptValidationException {
		Integer lineIndex = metadata.getLineIndex(params);
		if (lineIndex == null) {
			return null;
		}
		IndexRange lineRange = sgyFile.getLineRanges().get(lineIndex);
		if (lineRange == null) {
			throw new ScriptValidationException("Line " + lineIndex + " not found in the selected file");
		}
		return lineRange;
	}

	private void copy(SgyFile from, File to, @Nullable IndexRange lineRange) throws IOException {
		if (lineRange != null) {
			from.save(to, lineRange);
			return;
		}
		if (from instanceof TraceFile) {
			File file = from.getFile();
			if (file != null) {
				Files.copy(file.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			return;
		}
		from.save(to);
	}

	private List<String> buildCommand(File scriptFile, ScriptMetadata metadata,
	                                  Map<String, String> params, File tempFile) throws IOException {
		List<String> command = new ArrayList<>();
		command.add(pythonInterpreter.getPath().toString());
		command.add(scriptFile.toPath().toString());
		command.add(tempFile.toPath().toAbsolutePath().toString());
		appendArgs(command, metadata, params);
		return command;
	}

	// declared parameters first, in their order, then undeclared values given by the caller
	private static Map<String, String> resolveFileParams(SgyFile sgyFile, ScriptMetadata metadata,
	                                                     Map<String, String> params) {
		Map<String, String> fileParams = new LinkedHashMap<>();
		for (ScriptParameter param : metadata.parameters()) {
			String value = resolveValue(sgyFile, param, params.get(param.name()));
			if (value != null) {
				fileParams.put(param.name(), value);
			}
		}
		for (Map.Entry<String, String> entry : params.entrySet()) {
			fileParams.putIfAbsent(entry.getKey(), entry.getValue());
		}
		return fileParams;
	}

	@Nullable
	private static String resolveValue(SgyFile sgyFile, ScriptParameter param, @Nullable String givenValue) {
		if (param.type() == ScriptParameter.ParameterType.ORIGINAL_PATH) {
			return Check.notNull(sgyFile.getFile()).getAbsolutePath();
		}
		if (param.semantic() != null) {
			return param.isVisible() && !Strings.isNullOrEmpty(givenValue)
					? givenValue
					: param.getSemanticHeader(sgyFile);
		}
		return param.isVisible() ? givenValue : param.defaultValue();
	}

	public void appendArgs(List<String> command, ScriptMetadata metadata, Map<String, String> values) {
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String name = entry.getKey();
			String value = entry.getValue();
			if (metadata.isBooleanParameter(name)) {
				if (Boolean.parseBoolean(value)) {
					command.add("--" + name);
				}
			} else {
				command.add("--" + name);
				command.add(value);
			}
		}
	}

	private void applyResult(SgyFile sgyFile, File tempFile,
	                         Consumer<FileSnapshot<?>> onSnapshotCreated) throws IOException {
		FileSnapshot<?> snapshot = createFileSnapshot(sgyFile);
		try {
			loader.loadFrom(sgyFile, tempFile);
		} catch (Exception e) {
			if (snapshot != null) {
				snapshot.discard();
			}
			throw e;
		}
		if (snapshot == null) {
			log.warn("Undo snapshot unavailable for {}", sgyFile.getFile());
			return;
		}
		onSnapshotCreated.accept(snapshot);
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
}
