package com.ugcs.gprvisualizer.event;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.service.DistanceConverterService;

public class TemplateUnitChangedEvent extends BaseEvent {
    private final SgyFile file;
    private final String templateName;
    private final DistanceConverterService.Unit unit;

    public TemplateUnitChangedEvent(Object source, SgyFile file, String templateName, DistanceConverterService.Unit unit) {
        super(source);
        this.file = file;
        this.templateName = templateName;
        this.unit = unit;
    }

    public SgyFile getFile() {
        return file;
    }

    public String getTemplateName() {
        return templateName;
    }

    public DistanceConverterService.Unit getUnit() {
        return unit;
    }
}
