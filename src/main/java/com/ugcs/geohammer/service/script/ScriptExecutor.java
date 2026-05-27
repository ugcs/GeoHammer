package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

	public ScriptExecutor(Loader loader,
	                      EventSender eventSender,
	                      EventsFactory eventsFactory,
	                      CommandExecutor commandExecutor,
	                      PythonService pythonService) {
		this.loader = loader;
		this.eventSender = eventSender;
		this.eventsFactory = eventsFactory;
		this.commandExecutor = commandExecutor;
		this.pythonService = pythonService;
	}

	public void execute(SgyFile sgyFile, File scriptFile, ScriptMetadata metadata, Map<String, String> params,
	                    Consumer<String> output, Consumer<FileSnapshot<?>> onSnapshotCreated)
			throws IOException, InterruptedException {
		Check.notNull(sgyFile);
		Check.notNull(scriptFile);
		Check.notNull(metadata);

		File tempFile = createTempFile(sgyFile);
		try {
			copy(sgyFile, tempFile);

			List<String> command = buildCommand(scriptFile, metadata, params, tempFile);
			eventSender.send(eventsFactory.createScriptExecutionStartedEvent(metadata.filename()));
			commandExecutor.executeCommand(command, output);
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException();
			}

			applyResult(sgyFile, tempFile, onSnapshotCreated);
		} finally {
			if (!tempFile.delete()) {
				log.warn("Failed to delete temporary file: {}", tempFile);
			}
		}
	}

	private File createTempFile(SgyFile sgyFile) throws IOException {
		File source = Check.notNull(sgyFile.getFile());
		String fileName = source.getName();
		String prefix = FileNames.removeExtension(fileName);
		String suffix = "." + FileNames.getExtension(fileName);
		return Files.createTempFile(prefix, suffix).toFile();
	}

	private void copy(SgyFile from, File to) throws IOException {
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
		command.add(pythonService.getPythonPath().toString());
		command.add(scriptFile.toPath().toString());
		command.add(tempFile.toPath().toAbsolutePath().toString());
		metadata.appendArgs(command, params);
		return command;
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
