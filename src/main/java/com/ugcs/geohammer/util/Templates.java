package com.ugcs.geohammer.util;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.format.dzt.DztFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public final class Templates {

    private Templates() {
        // Utility class, no instantiation
    }

    public static boolean equals(SgyFile a, SgyFile b) {
        return Objects.equals(getTemplateName(a), getTemplateName(b));
    }

    public static boolean equals(@Nullable Template a, @Nullable Template b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getName(), b.getName());
    }

    public static Template getTemplate(SgyFile file) {
        return file instanceof CsvFile csvFile
                ? csvFile.getTemplate()
                : null;
    }

    public static String getTemplateName(SgyFile file) {
        if (file == null) {
            return null;
        }
        return switch (file) {
            case CsvFile csvFile -> getCsvTemplateName(csvFile);
            case GprFile gprFile -> "sgy";
            case DztFile dztFile -> "dzt";
            case SonarFile dztFile -> "sonar";
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
