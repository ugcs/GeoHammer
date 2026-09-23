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

public class McpSession {

    private final String id;

    private volatile Instant lastActive;

    // file versions as last read by the session; identity keys,
    // as the file hash depends on its path that changes on save as
    private final IdentityHashMap<SgyFile, Long> readVersions = new IdentityHashMap<>();

    // undo frames of the modifications made by the session, latest last
    private final Deque<UndoFrame> undoFrames = new ArrayDeque<>();

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

    public boolean isIdle(Instant now, Duration timeout) {
        return Duration.between(lastActive, now).compareTo(timeout) > 0;
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
