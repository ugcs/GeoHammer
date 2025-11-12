package com.ugcs.geohammer.model.undo;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.Check;

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

    public abstract void restoreFile(Model model);

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

        file.tracesChanged();

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
