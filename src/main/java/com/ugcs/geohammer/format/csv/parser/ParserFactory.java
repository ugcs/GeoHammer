package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;

public class ParserFactory {

    public static Parser createParser(Template template) {
        Check.notNull(template);

        return switch (Check.notNull(template.getFileType())) {
            case CSV -> new CsvParser(template);
            case ColumnsFixedWidth -> new FixedWidthParser(template);
            default -> throw new IllegalArgumentException("Unsupported file type: " + template.getFileType());
        };
    }
}
