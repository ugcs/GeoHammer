package com.ugcs.gprvisualizer.event;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.TraceUnit;

public class TemplateUnitChangedEvent extends BaseEvent {
    private final SgyFile file;
    private final String templateName;
    private final TraceUnit traceUnit;

    public TemplateUnitChangedEvent(Object source, SgyFile file, String templateName, TraceUnit traceUnit) {
        super(source);
        this.file = file;
        this.templateName = templateName;
        this.traceUnit = traceUnit;
    }

    public SgyFile getFile() {
        return file;
    }

    public String getTemplateName() {
        return templateName;
    }

    public TraceUnit getUnit() {
        return traceUnit;
    }
}
