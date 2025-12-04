package com.ugcs.geohammer.model;

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

    public @Nullable String getSelectedSeriesName(String templateName) {
        return settings.getString(templateName,"selected");
    }

    public void setSelectedSeriesName(String templateName, String seriesName) {
        settings.setValue(templateName, "selected", seriesName);
    }

    public @Nullable Boolean isSeriesVisible(String templateName, String seriesName) {
        return settings.getBoolean(templateName + "." + seriesName, "visible");
    }

    public void setSeriesVisible(String templateName, String seriesName, boolean visible) {
        settings.setValue(templateName + "." + seriesName, "visible", visible);
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
