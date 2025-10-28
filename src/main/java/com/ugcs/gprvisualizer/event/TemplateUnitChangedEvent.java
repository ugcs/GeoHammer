package com.ugcs.gprvisualizer.event;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.TraceUnit;

public class TemplateUnitChangedEvent extends BaseEvent {
    private final SgyFile file;
    private final TraceUnit traceUnit;

    public TemplateUnitChangedEvent(Object source, SgyFile file, TraceUnit traceUnit) {
        super(source);
        this.file = file;
        this.traceUnit = traceUnit;
    }

    public SgyFile getFile() {
        return file;
    }

    public TraceUnit getUnit() {
        return traceUnit;
    }
}
