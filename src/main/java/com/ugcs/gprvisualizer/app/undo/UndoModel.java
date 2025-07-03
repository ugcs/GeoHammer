package com.ugcs.gprvisualizer.app.undo;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.UndoStackChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Check;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

@Component
public class UndoModel {

    @Value("${undo.depth:3}")
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
        if (frame == null) {
            return;
        }
        // push undo frame
        frames.addFirst(frame);
        // maintain undo depth
        if (frames.size() > undoDepth) {
            frames.removeLast();
        }
        model.publishEvent(new UndoStackChanged(this));
    }

    public void removeSnapshots(SgyFile file) {
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

    public boolean canUndo() {
        return !frames.isEmpty();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }

        // peek top frame
        UndoFrame frame = frames.removeFirst();
        // restore frame state
        frame.restore(model);

        model.publishEvent(new UndoStackChanged(this));
    }

    @EventListener
    private void fileClosed(FileClosedEvent event) {
        SgyFile file = event.getSgyFile();
        if (file != null) {
            removeSnapshots(file);
        }
    }
}
