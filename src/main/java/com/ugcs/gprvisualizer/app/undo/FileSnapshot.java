package com.ugcs.gprvisualizer.app.undo;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.Chart;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.ext.FileManager;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.AuxElements;
import com.ugcs.gprvisualizer.utils.Check;

import java.util.ArrayList;
import java.util.List;

public abstract class FileSnapshot<T extends SgyFile> implements UndoSnapshot {

    protected final T file;

    protected final List<BaseObject> elements;

    public FileSnapshot(T file) {
        this.file = file;

        this.elements = AuxElements.copy(file.getAuxElements());
    }

    public T getFile() {
        return file;
    }

    abstract void restoreFile(Model model);

    @Override
    public void restore(Model model) {
        if (!isFileOpened(model)) {
            return; // file was closed
        }

        restoreFile(model);

        file.setAuxElements(elements);

        onFileChanged(model);
    }

    private boolean isFileOpened(Model model) {
        Check.notNull(model);
        Check.notNull(file);

        FileManager fileManager = model.getFileManager();
        return fileManager.getFiles().contains(file);
    }

    private void onFileChanged(Model model) {
        Check.notNull(model);
        Check.notNull(file);

        file.rebuildLineRanges();

        // update file chart
        Chart chart = model.getFileChart(file);
        if (chart != null) {
            chart.reload();
        }

        // update model
        model.updateAuxElements();

        // request redraw
        model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
        model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
    }
}
