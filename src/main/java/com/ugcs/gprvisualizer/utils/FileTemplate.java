package com.ugcs.gprvisualizer.utils;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.gpr.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public final class FileTemplate {

    private static final Logger log = LoggerFactory.getLogger(FileTemplate.class);

    private FileTemplate() {
        // Utility class, no instantiation
    }

    @Nullable
    public static String getTemplateName(Model model, @Nullable File file) {
        if (FileTypes.isGprFile(file)) {
            return "sgy";
        } else if (FileTypes.isDztFile(file)) {
            return "dzt";
        } else if (FileTypes.isCsvFile(file)) {
            String csvTemplateName = getCsvTemplateName(model, file);
            return csvTemplateName != null ? csvTemplateName : file.getName();
        }
        return null;
    }

    @Nullable
    private static String getCsvTemplateName(Model model, File file) {
        List<CsvFile> csvFiles = model.getFileManager().getCsvFiles();
        CsvFile csvFile = csvFiles.stream()
                .filter(f -> f.getFile() != null && f.getFile().equals(file))
                .findFirst()
                .orElse(null);
        if (csvFile == null) {
            if (FileTypes.isCsvFile(file)) {
                log.warn("CSV file not found in model: {}", file.getName());
            }
            return null;
        }
        CsvParser parser = csvFile.getParser();
        if (parser == null) {
            log.warn("CSV file parser is not initialized for file: {}", file.getName());
            return null;
        }
        return parser.getTemplate().getName();
    }
}
