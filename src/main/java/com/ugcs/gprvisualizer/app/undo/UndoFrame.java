package com.ugcs.gprvisualizer.app.undo;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class UndoFrame {

    private final List<UndoSnapshot> snapshots;

    public UndoFrame(List<UndoSnapshot> snapshots) {
        Check.notNull(snapshots);

        this.snapshots = snapshots;
    }

    public List<UndoSnapshot> getSnapshots() {
        return snapshots;
    }

    private static UndoSnapshot createSnapshot(SgyFile file) {
        if (file instanceof TraceFile traceFile) {
            return new GprSnapshot(traceFile);
        }
        if (file instanceof CsvFile csvFile) {
            return new CsvSnapshot(csvFile);
        }
        return null;
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
