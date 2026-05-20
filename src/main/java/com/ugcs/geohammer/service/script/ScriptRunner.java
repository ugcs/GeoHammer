package com.ugcs.geohammer.service.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.model.undo.UndoSnapshot;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ScriptRunner {

    private static final int MAX_BUFFERED_OUTPUT_LINES = 7;

    private final ScriptExecutor scriptExecutor;

	private final UndoModel undoModel;

    public ScriptRunner(ScriptExecutor scriptExecutor, UndoModel undoModel) {
        this.scriptExecutor = scriptExecutor;
		this.undoModel = undoModel;
    }

    public boolean isExecuting(SgyFile sgyFile) {
        return scriptExecutor.isExecuting(sgyFile);
    }

    @Nullable
    public ScriptMetadata getExecutingScriptMetadata(SgyFile sgyFile) {
        return scriptExecutor.getExecutingScriptMetadata(sgyFile);
    }

    public void run(List<SgyFile> files, ScriptMetadata metadata, Map<String, String> params,
                    Consumer<String> onOutput, ScriptRunListener listener) {
        Deque<String> outputBuffer = new ArrayDeque<>();
        Consumer<String> bufferedOutput = line -> {
            outputBuffer.offer(line);
            if (outputBuffer.size() > MAX_BUFFERED_OUTPUT_LINES) {
                outputBuffer.poll();
            }
            onOutput.accept(line);
        };

		List<UndoSnapshot> snapshots = new ArrayList<>(files.size());
        for (SgyFile sgyFile : files) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            outputBuffer.clear();
			FileSnapshot<?> snapshot = runOnFile(sgyFile, metadata, params, bufferedOutput, outputBuffer, listener);
            if (snapshot == null) {
                return;
            }
			snapshots.add(snapshot);
        }
		undoModel.push(new UndoFrame(snapshots));
    }

    private @Nullable FileSnapshot<?> runOnFile(SgyFile sgyFile, ScriptMetadata metadata, Map<String, String> params,
                              Consumer<String> output, Deque<String> outputBuffer,
                              ScriptRunListener listener) {
        try {
			FileSnapshot<?> snapshot = scriptExecutor.executeScript(sgyFile, metadata, params, output);
            listener.onSuccess(metadata);
            return snapshot;
        } catch (DependencyImportException e) {
            if (!listener.confirmReinstallDependencies(e.getModuleName())) {
                listener.onError(metadata, e, drain(outputBuffer));
                return null;
            }
            return retryWithReinstall(sgyFile, metadata, params, output, outputBuffer, listener);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            listener.onError(metadata, e, drain(outputBuffer));
            return null;
        }
    }

    // Returns true if the retry succeeded (loop may continue), false if it failed (loop should stop).
    private @Nullable FileSnapshot<?> retryWithReinstall(SgyFile sgyFile, ScriptMetadata metadata, Map<String, String> params,
                                       Consumer<String> output, Deque<String> outputBuffer,
                                       ScriptRunListener listener) {
        try {
			FileSnapshot<?> snapshot = scriptExecutor.executeScriptWithReinstall(sgyFile, metadata, params, output);
            listener.onSuccess(metadata);
            return snapshot;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            listener.onError(metadata, e, drain(outputBuffer));
            return null;
        }
    }

    private static String drain(Deque<String> buffer) {
        return String.join(System.lineSeparator(), buffer);
    }
}
