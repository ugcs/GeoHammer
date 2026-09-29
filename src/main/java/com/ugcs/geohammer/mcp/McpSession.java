package com.ugcs.geohammer.mcp;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpSession {

    private final String id;

    private volatile Instant lastActive;

    // file versions as last read by the session; identity keys,
    // as the file hash depends on its path that changes on save as
    private final IdentityHashMap<SgyFile, Long> readVersions = new IdentityHashMap<>();

    // undo frames of the modifications made by the session, latest last
    private final Deque<UndoFrame> undoFrames = new ArrayDeque<>();

    // tools/call requests in progress by request id
    private final Map<String, McpCall> calls = new ConcurrentHashMap<>();

    public McpSession(String id, Instant now) {
        this.id = Check.notNull(id);
        this.lastActive = Check.notNull(now);
    }

    public String getId() {
        return id;
    }

    public void touch(Instant now) {
        lastActive = now;
    }

    // a session is never idle while its calls run, however long they take
    public boolean isIdle(Instant now, Duration timeout) {
        return calls.isEmpty() && Duration.between(lastActive, now).compareTo(timeout) > 0;
    }

    public void startCall(String requestId, McpCall call) {
        calls.put(requestId, call);
    }

    public void finishCall(String requestId) {
        calls.remove(requestId);
        touch(Instant.now());
    }

    // a request that is unknown or already finished is ignored
    public void cancelCall(String requestId) {
        McpCall call = calls.get(requestId);
        if (call != null) {
            call.cancel();
        }
    }

    public synchronized void trackRead(SgyFile file) {
        readVersions.put(file, file.getVersion());
    }

    // a file the session has not read yet can be written without a check
    public synchronized void checkWrite(SgyFile file) {
        Long readVersion = readVersions.get(file);
        if (readVersion != null && readVersion != file.getVersion()) {
            throw new FileModifiedException(file.getFile());
        }
    }

    public synchronized void untrack(SgyFile file) {
        readVersions.remove(file);
    }

    public synchronized void pushUndoFrame(UndoFrame frame, UndoModel undoModel) {
        removeStaleUndoFrames(undoModel);
        undoFrames.addLast(frame);
    }

    public synchronized @Nullable UndoFrame peekUndoFrame(UndoModel undoModel) {
        removeStaleUndoFrames(undoModel);
        return undoFrames.peekLast();
    }

    // frames no longer in the undo stack: undone, evicted by the undo depth
    // or removed with a closed file
    public synchronized void removeStaleUndoFrames(UndoModel undoModel) {
        undoFrames.removeIf(frame -> !undoModel.contains(frame));
    }
}
