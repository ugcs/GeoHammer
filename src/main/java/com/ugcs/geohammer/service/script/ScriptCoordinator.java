package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.model.undo.UndoSnapshot;
import com.ugcs.geohammer.service.TaskService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScriptCoordinator {

	private static final Logger log = LoggerFactory.getLogger(ScriptCoordinator.class);

	private static final int MAX_RECENT_OUTPUT_LINES = 15;

	private final ScriptExecutor scriptExecutor;

	private final UndoModel undoModel;

	private final PythonService pythonService;

	private final ScriptPaths scriptPaths;

	private final TaskService taskService;

	private final ExecutorService executor;

	private final Map<SgyFile, ScriptMetadata> executingScripts = new ConcurrentHashMap<>();

	public ScriptCoordinator(ScriptExecutor scriptExecutor, UndoModel undoModel,
	                         PythonService pythonService, ScriptPaths scriptPaths,
	                         TaskService taskService, ExecutorService executor) {
		this.scriptExecutor = scriptExecutor;
		this.undoModel = undoModel;
		this.pythonService = pythonService;
		this.scriptPaths = scriptPaths;
		this.taskService = taskService;
		this.executor = executor;
	}

	public void submit(List<SgyFile> sgyFiles, ScriptMetadata metadata, Map<String, String> params,
	                   Consumer<String> onOutput, ScriptRunListener listener) {
		RecentOutput recent = new RecentOutput(onOutput, MAX_RECENT_OUTPUT_LINES);
		Future<Void> future = executor.submit(() -> {
			listener.onRunStarted();
			try {
				runScriptOnFiles(sgyFiles, metadata, params, recent, listener);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				log.error(formatErrorOutput(recent, e));
				listener.onError(metadata, e, formatErrorOutput(recent, e));
			} finally {
				listener.onRunFinished();
			}
			return null;
		});
		taskService.registerTask(future, buildTaskName(metadata, sgyFiles));
	}

	private static String formatErrorOutput(RecentOutput recent, Throwable t) {
		String captured = recent.drain();
		if (!captured.isEmpty()) {
			return captured;
		}
		String message = t.getMessage();
		return message != null ? message : "";
	}

	public boolean isExecuting(@Nullable SgyFile sgyFile) {
		return sgyFile != null && executingScripts.containsKey(sgyFile);
	}

	@Nullable
	public ScriptMetadata getExecutingScriptMetadata(SgyFile sgyFile) {
		return executingScripts.get(sgyFile);
	}

	private void runScriptOnFiles(List<SgyFile> sgyFiles, ScriptMetadata metadata, Map<String, String> params,
	                              RecentOutput recent, ScriptRunListener listener)
			throws InterruptedException, IOException {
		pythonService.checkVersion();

		File scriptFile = resolveScriptFile(metadata);

		List<UndoSnapshot> snapshots = new ArrayList<>(sgyFiles.size());
		try {
			for (SgyFile sgyFile : sgyFiles) {
				if (Thread.currentThread().isInterrupted()) {
					return;
				}
				if (isExecuting(sgyFile)) {
					log.warn("Script is already running for {}", sgyFile.getFile());
					continue;
				}
				recent.clear();
				executingScripts.put(sgyFile, metadata);
				try {
					if (!ensureDependencies(scriptFile, recent, metadata, listener)) {
						return;
					}
					try {
						scriptExecutor.execute(sgyFile, scriptFile, metadata, params, recent.capture(), snapshots::add);
						listener.onSuccess(metadata);
					} catch (Exception e) {
						listener.onError(metadata, e, formatErrorOutput(recent, e));
					}
				} finally {
					executingScripts.remove(sgyFile);
				}
			}
		} finally {
			if (!snapshots.isEmpty()) {
				undoModel.push(new UndoFrame(snapshots));
			}
		}
	}

	private File resolveScriptFile(ScriptMetadata metadata) throws IOException {
		File scriptFile = new File(scriptPaths.getScriptsPath().toFile(), metadata.filename());
		if (!scriptFile.exists()) {
			throw new IOException("Script file not found: " + scriptFile.getAbsolutePath());
		}
		return scriptFile;
	}

	private String buildTaskName(ScriptMetadata scriptMetadata, List<SgyFile> files) {
		String taskName = "Running script " + scriptMetadata.displayName();
		if (files.size() == 1) {
			SgyFile sgyFile = files.getFirst();
			if (sgyFile.getFile() != null) {
				taskName += ": " + sgyFile.getFile().getName();
			}
		} else if (files.size() > 1) {
			taskName += " on " + files.size() + " files";
		}
		return taskName;
	}

	private boolean ensureDependencies(File scriptFile, RecentOutput recent,
	                                   ScriptMetadata metadata, ScriptRunListener listener)
			throws InterruptedException {
		try {
			pythonService.installDependencies(scriptFile, recent.capture());
			return true;
		} catch (DependencyImportException e) {
			if (!listener.confirmReinstallDependencies(e.getModuleName())) {
				listener.onError(metadata, e, recent.drain());
				return false;
			}
		} catch (IOException e) {
			listener.onError(metadata, e, recent.drain());
			return false;
		}

		try {
			pythonService.reinstallDependencies(scriptFile, recent.capture());
			return true;
		} catch (IOException | DependencyImportException e) {
			listener.onError(metadata, e, recent.drain());
			return false;
		}
	}

	private static final class RecentOutput {

		private final Deque<String> lines = new ArrayDeque<>();

		private final Consumer<String> downstream;

		private final int maxLines;

		RecentOutput(Consumer<String> downstream, int maxLines) {
			this.downstream = downstream;
			this.maxLines = maxLines;
		}

		Consumer<String> capture() {
			return line -> {
				lines.offer(line);
				if (lines.size() > maxLines) {
					lines.poll();
				}
				downstream.accept(line);
			};
		}

		void clear() {
			lines.clear();
		}

		String drain() {
			return String.join(System.lineSeparator(), lines);
		}
	}
}
