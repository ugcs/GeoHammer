package com.ugcs.gprvisualizer.app.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ugcs.gprvisualizer.app.TraceUnit;
import org.springframework.stereotype.Service;

@Service
public class TemplateSettingsModel {
    private final Map<String, TraceUnit> templateUnits = new ConcurrentHashMap<>();

    public void setUnitForTemplate(String templateName, TraceUnit traceUnit) {
        templateUnits.put(templateName, traceUnit);
    }

    public TraceUnit getUnitForTemplate(String templateName) {
        return templateUnits.get(templateName);
    }

    public boolean hasUnitForTemplate(String templateName) {
        return templateUnits.containsKey(templateName);
    }
}
