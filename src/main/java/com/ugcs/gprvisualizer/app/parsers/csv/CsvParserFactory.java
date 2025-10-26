package com.ugcs.gprvisualizer.app.parsers.csv;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.utils.Check;

public class CsvParserFactory {

    public CsvParser createCsvParser(Template template) {
        Check.notNull(template);

        return new CsvParser(template);
    }
}
