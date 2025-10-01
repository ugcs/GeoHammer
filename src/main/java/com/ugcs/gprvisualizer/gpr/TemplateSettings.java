package com.ugcs.gprvisualizer.gpr;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Strings;
import org.springframework.stereotype.Service;

@Service
public class TemplateSettings {

    private final PrefSettings settings;

    public TemplateSettings(PrefSettings settings) {
        Check.notNull(settings);
        this.settings = settings;
    }

    public String getSelectedSeriesName(Template template) {
        return settings.getSetting(template.getName(),"selected");
    }

    public void setSelectedSeriesName(Template template, String seriesName) {
        settings.saveSetting(template.getName(), "selected", seriesName);
    }

    public boolean isSeriesVisible(Template template, String seriesName) {
        String s = settings.getSetting(template.getName() + "." + seriesName, "visible");
        return Strings.isNullOrEmpty(s) || Boolean.parseBoolean(s);
    }

    public void setSeriesVisible(Template template, String seriesName, boolean visible) {
        settings.saveSetting(template.getName() + "." + seriesName, "visible", visible);
    }
}
