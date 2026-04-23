package com.ugcs.geohammer.service.script;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.SgyFile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ScriptRunner {

    private static final int MAX_BUFFERED_OUTPUT_LINES = 7;

    private final ScriptExecutor scriptExecutor;

    public ScriptRunner(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
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

        for (SgyFile sgyFile : files) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            outputBuffer.clear();
            if (!runOnFile(sgyFile, metadata, params, bufferedOutput, outputBuffer, listener)) {
                return;
            }
        }
    }

    private boolean runOnFile(SgyFile sgyFile, ScriptMetadata metadata, Map<String, String> params,
                              Consumer<String> output, Deque<String> outputBuffer,
                              ScriptRunListener listener) {
        try {
            scriptExecutor.executeScript(sgyFile, metadata, params, output);
            listener.onSuccess(metadata);
            return true;
        } catch (DependencyImportException e) {
            if (!listener.confirmReinstallDependencies(e.getModuleName())) {
                listener.onError(metadata, e, drain(outputBuffer));
                return false;
            }
            return retryWithReinstall(sgyFile, metadata, params, output, outputBuffer, listener);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            listener.onError(metadata, e, drain(outputBuffer));
            return true;
        }
    }

    // Returns true if the retry succeeded (loop may continue), false if it failed (loop should stop).
    private boolean retryWithReinstall(SgyFile sgyFile, ScriptMetadata metadata, Map<String, String> params,
                                       Consumer<String> output, Deque<String> outputBuffer,
                                       ScriptRunListener listener) {
        try {
            scriptExecutor.executeScriptWithReinstall(sgyFile, metadata, params, output);
            listener.onSuccess(metadata);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            listener.onError(metadata, e, drain(outputBuffer));
            return false;
        }
    }

    private static String drain(Deque<String> buffer) {
        return String.join(System.lineSeparator(), buffer);
    }
}
