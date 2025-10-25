package com.ugcs.gprvisualizer.app.parsers.csv;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.utils.Check;

public class CsvParsersFactory {

    private static final String MAGDRONE_TEMPLATE_CODE = "magdrone";
    
    public CsvParser createCsvParser(Template template) {
        Check.notNull(template);

        return switch (template.getCode()) {
            case MAGDRONE_TEMPLATE_CODE -> new MagDroneCsvParser(template);
            default -> new CsvParser(template);
        };
    }
}
