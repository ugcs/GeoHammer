package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;

public class WriterFactory {

    public static Writer createWriter(Template template) {
        Check.notNull(template);

        return switch (Check.notNull(template.getFileType())) {
            case CSV -> new CsvWriter(template);
            case ColumnsFixedWidth -> new FixedWidthWriter(template);
            default -> throw new IllegalArgumentException("Unsupported file type: " + template.getFileType());
        };
    }
}
