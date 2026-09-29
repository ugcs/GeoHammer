package com.ugcs.geohammer.mcp;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// A tools/call in progress: carries progress notifications to the client
// and the client's cancellation to the tool.
public class McpCall {

    private static final Logger log = LoggerFactory.getLogger(McpCall.class);

    // null when the client did not ask for progress
    @Nullable private final ProgressListener progressListener;

    // actions to run on cancellation
    private final List<Runnable> cancelActions = new ArrayList<>();

    private volatile boolean cancelled;

    public McpCall(@Nullable ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    // progress must increase with every report
    public void reportProgress(double progress, String message) {
        if (progressListener == null || isCancelled()) {
            return;
        }
        try {
            progressListener.onProgress(progress, message);
        } catch (IOException e) {
            log.warn("MCP client disconnected, the call is cancelled");
            cancel();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        List<Runnable> actions;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            actions = List.copyOf(cancelActions);
            cancelActions.clear();
        }
        for (Runnable action : actions) {
            action.run();
        }
    }

    // the action runs once the call is cancelled, immediately if it already is
    public void onCancel(Runnable action) {
        synchronized (this) {
            if (!cancelled) {
                cancelActions.add(action);
                return;
            }
        }
        action.run();
    }

    public interface ProgressListener {

        void onProgress(double progress, String message) throws IOException;
    }
}
