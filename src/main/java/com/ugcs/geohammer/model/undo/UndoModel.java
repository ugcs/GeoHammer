package com.ugcs.geohammer.model.undo;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.UndoStackChanged;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

@Component
public class UndoModel {

    @Value("${undo.depth:30}")
    private int undoDepth;

    private final Deque<UndoFrame> frames = new ArrayDeque<>();

    private final Model model;

    public UndoModel(Model model) {
        this.model = model;
    }

    public void saveSnapshot(SgyFile file) {
        saveSnapshot(List.of(file));
    }

    public void saveSnapshot(Collection<SgyFile> files) {
        UndoFrame frame = UndoFrame.create(files);
        push(frame);
    }

    public synchronized void removeSnapshots(SgyFile file) {
        Check.notNull(file);

        Iterator<UndoFrame> it = frames.iterator();
        while (it.hasNext()) {
            UndoFrame frame = it.next();
            frame.removeSnapshots(file);
            if (frame.getSnapshots().isEmpty()) {
                it.remove();
                model.publishEvent(new UndoStackChanged(this));
            }
        }
    }

    public synchronized void push(UndoFrame frame) {
        if (frame == null) {
            return;
        }
        // push undo frame
        frames.addFirst(frame);
        // maintain undo depth
        if (frames.size() > undoDepth) {
            UndoFrame evicted = frames.removeLast();
            evicted.discard();
        }
        model.publishEvent(new UndoStackChanged(this));
    }

    public synchronized boolean canUndo() {
        return !frames.isEmpty();
    }

    public synchronized @Nullable UndoFrame peek() {
        return frames.peekFirst();
    }

    // top frame if it was pushed after the given frame, which is then still below it;
    // null if nothing was pushed since or the given frame was removed from the stack
    public synchronized @Nullable UndoFrame peekPushedAfter(@Nullable UndoFrame frame) {
        UndoFrame top = frames.peekFirst();
        if (top == null || top == frame) {
            return null;
        }
        return frame == null || frames.contains(frame) ? top : null;
    }

    public synchronized boolean contains(UndoFrame frame) {
        return frames.contains(frame);
    }

    // files of the frame; snapshots of closed files are removed from frames under the same monitor
    public synchronized List<SgyFile> getFiles(UndoFrame frame) {
        Check.notNull(frame);

        List<SgyFile> files = new ArrayList<>();
        for (UndoSnapshot snapshot : frame.getSnapshots()) {
            if (snapshot instanceof FileSnapshot<?> fileSnapshot) {
                files.add(fileSnapshot.getFile());
            }
        }
        return files;
    }

    public void undo() {
        UndoFrame frame = peek();
        if (frame != null) {
            undo(frame);
        }
    }

    // undoes the frame only if it is still on top of the stack
    public boolean undo(UndoFrame frame) {
        Check.notNull(frame);
        if (!removeFirst(frame)) {
            return false;
        }
        // restore frame state outside the monitor
        frame.restore(model);
        frame.discard();

        model.publishEvent(new UndoStackChanged(this));
        return true;
    }

    private synchronized boolean removeFirst(UndoFrame frame) {
        if (frames.peekFirst() != frame) {
            return false;
        }
        frames.removeFirst();
        return true;
    }

    @EventListener
    private void fileClosed(FileClosedEvent event) {
        SgyFile file = event.getFile();
        if (file != null) {
            removeSnapshots(file);
        }
    }
}
