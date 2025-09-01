package com.ugcs.gprvisualizer.app.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class TemplateUnitService {
    private final Map<String, DistanceConverterService.Unit> templateUnits = new ConcurrentHashMap<>();

    public void setUnitForTemplate(String templateName, DistanceConverterService.Unit unit) {
        templateUnits.put(templateName, unit);
    }

    public DistanceConverterService.Unit getUnitForTemplate(String templateName) {
        return templateUnits.get(templateName);
    }

    public boolean hasUnitForTemplate(String templateName) {
        return templateUnits.containsKey(templateName);
    }
}
