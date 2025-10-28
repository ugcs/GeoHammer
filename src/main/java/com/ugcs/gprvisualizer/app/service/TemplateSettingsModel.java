package com.ugcs.gprvisualizer.app.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ugcs.gprvisualizer.app.TraceUnit;
import com.ugcs.gprvisualizer.utils.Strings;
import org.springframework.stereotype.Service;

@Service
public class TemplateSettingsModel {

    private final Map<String, TraceUnit> traceUnits = new ConcurrentHashMap<>();

    public void setTraceUnit(String templateName, TraceUnit traceUnit) {
        if (Strings.isNullOrEmpty(templateName)) {
            return;
        }
        if (traceUnit == null) {
            traceUnit = TraceUnit.getDefault();
        }
        traceUnits.put(templateName, traceUnit);
    }

    public TraceUnit getTraceUnit(String templateName) {
        if (Strings.isNullOrEmpty(templateName)) {
            return TraceUnit.getDefault();
        }
        return traceUnits.getOrDefault(templateName, TraceUnit.getDefault());
    }
}
