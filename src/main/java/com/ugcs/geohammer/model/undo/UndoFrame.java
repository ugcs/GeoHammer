package com.ugcs.geohammer.model.undo;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class UndoFrame {

    private final List<UndoSnapshot> snapshots;

    public UndoFrame(List<UndoSnapshot> snapshots) {
        Check.notNull(snapshots);

        this.snapshots = snapshots;
    }

    public UndoFrame(UndoSnapshot snapshot) {
        Check.notNull(snapshot);

        this.snapshots = new ArrayList<>();
        this.snapshots.add(snapshot);
    }

    public List<UndoSnapshot> getSnapshots() {
        return snapshots;
    }

    private static UndoSnapshot createSnapshot(SgyFile file) {
        if (file == null) {
            return null;
        }
        return file.createSnapshot();
    }

    public static UndoFrame create(Collection<SgyFile> files) {
        if (files == null) {
            return null;
        }

        List<UndoSnapshot> snapshots = new ArrayList<>(files.size());
        for (SgyFile file : files) {
            Check.notNull(file);

            UndoSnapshot snapshot = createSnapshot(file);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return !snapshots.isEmpty()
                ? new UndoFrame(snapshots)
                : null;
    }

    public void removeSnapshots(SgyFile file) {
        Check.notNull(file);

        snapshots.removeIf(s ->
                s instanceof FileSnapshot<?> fileSnapshot
                        && Objects.equals(fileSnapshot.getFile(), file));
    }

    public void restore(Model model) {
        for (UndoSnapshot snapshot : snapshots) {
            snapshot.restore(model);
        }
    }
}
