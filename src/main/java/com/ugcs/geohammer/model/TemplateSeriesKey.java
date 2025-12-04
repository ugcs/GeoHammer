package com.ugcs.geohammer.model;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;

public record TemplateSeriesKey(String templateName, String seriesName) {

    public static TemplateSeriesKey ofSeries(SgyFile file, String seriesName) {
        if (file == null) {
            return null;
        }
        String templateName = Templates.getTemplateName(file);
        return !Strings.isNullOrEmpty(templateName) && !Strings.isNullOrEmpty(seriesName)
                ? new TemplateSeriesKey(templateName, seriesName)
                : null;
    }
}
