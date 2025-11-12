package com.ugcs.geohammer.model;

import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemplateSettings {

    private final PrefSettings settings;

    private final Map<String, TraceUnit> traceUnits = new ConcurrentHashMap<>();

    public TemplateSettings(PrefSettings settings) {
        Check.notNull(settings);
        this.settings = settings;
    }

    public @Nullable String getSelectedSeriesName(Template template) {
        return settings.getSetting(template.getName(),"selected");
    }

    public void setSelectedSeriesName(Template template, String seriesName) {
        settings.saveSetting(template.getName(), "selected", seriesName);
    }

    public @Nullable Boolean isSeriesVisible(Template template, String seriesName) {
        String s = settings.getSetting(template.getName() + "." + seriesName, "visible");
        return !Strings.isNullOrEmpty(s) ? Boolean.parseBoolean(s) : null;
    }

    public void setSeriesVisible(Template template, String seriesName, boolean visible) {
        settings.saveSetting(template.getName() + "." + seriesName, "visible", visible);
    }

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
