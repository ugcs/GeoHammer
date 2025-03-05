package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.gpr.Model;

import java.util.List;

public abstract class Chart extends ScrollableData implements FileDataContainer {

    protected final Model model;

    public Chart(Model model) {
        super(model);

        this.model = model;
    }

    public abstract List<SgyFile> getFiles();

    // trace == null -> clear current selection
    public abstract void selectTrace(Trace trace, boolean focus);

    // flags

    public abstract List<FoundPlace> getFlags();

    public abstract void selectFlag(FoundPlace flag);

    public abstract void addFlag(FoundPlace flag);

    public abstract void removeFlag(FoundPlace flag);

    public abstract void clearFlags();
}
