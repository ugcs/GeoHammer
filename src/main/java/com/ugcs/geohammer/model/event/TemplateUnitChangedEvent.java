package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.TraceUnit;

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
