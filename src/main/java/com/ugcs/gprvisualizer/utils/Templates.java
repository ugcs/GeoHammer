package com.ugcs.gprvisualizer.utils;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.dzt.DztFile;

public final class Templates {

    private Templates() {
        // Utility class, no instantiation
    }

    public static String getTemplateName(SgyFile file) {
        if (file == null) {
            return null;
        }
        return switch (file) {
            case CsvFile csvFile -> getCsvTemplateName(csvFile);
            case GprFile gprFile -> "sgy";
            case DztFile dztFile -> "dzt";
            default -> null;
        };
    }

    public static String getCsvTemplateName(CsvFile csvFile) {
        if (csvFile == null) {
            return null;
        }
        Template template = csvFile.getTemplate();
        if (template == null) {
            return null;
        }
        return template.getName();
    }
}
