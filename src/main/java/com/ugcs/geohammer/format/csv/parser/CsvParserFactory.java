package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;

public class CsvParserFactory {

    public CsvParser createCsvParser(Template template) {
        Check.notNull(template);

        return new CsvParser(template);
    }
}
