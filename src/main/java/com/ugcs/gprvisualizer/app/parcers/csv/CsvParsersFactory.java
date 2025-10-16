package com.ugcs.gprvisualizer.app.parcers.csv;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.utils.Check;

public class CsvParsersFactory {

    private static final String MAGDRONE_TEMPLATE_CODE = "magdrone";
    
    private static final String NMEA_TEMPLATE_CODE = "nmea";

    public CsvParser createCsvParser(Template template) {
        Check.notNull(template);

        return switch (template.getCode()) {
            case MAGDRONE_TEMPLATE_CODE -> new MagDroneCsvParser(template);
            case NMEA_TEMPLATE_CODE -> new NmeaCsvParser(template);
            default -> new CsvParser(template);
        };
    }
}
