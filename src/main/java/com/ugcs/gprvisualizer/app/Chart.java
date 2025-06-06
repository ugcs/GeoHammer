package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.gpr.Model;
import org.jspecify.annotations.Nullable;

import java.util.List;

public abstract class Chart extends ScrollableData implements FileDataContainer {

    protected final Model model;

    public Chart(Model model) {
        super(model);

        this.model = model;
    }

    public abstract SgyFile getFile();

    // trace == null -> clear current selection
    public abstract void selectTrace(@Nullable TraceKey trace, boolean focus);

    // flags

    public abstract List<FoundPlace> getFlags();

    public abstract void selectFlag(@Nullable FoundPlace flag);

    public abstract void addFlag(FoundPlace flag);

    public abstract void removeFlag(FoundPlace flag);

    public abstract void clearFlags();

    // zoom

    public abstract void zoomToCurrentLine();

    public abstract void zoomToPreviousLine();

    public abstract void zoomToNextLine();

    public abstract void zoomToFit();

    public abstract void zoomIn();

    public abstract void zoomOut();
}