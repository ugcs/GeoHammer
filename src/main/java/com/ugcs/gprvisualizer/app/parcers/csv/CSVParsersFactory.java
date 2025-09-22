package com.ugcs.gprvisualizer.app.parcers.csv;

import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.gpr.PrefSettings;

public class CSVParsersFactory {

    private final String MAGDRONE = "magdrone";
    
    private final String NMEA = "nmea";

    public CsvParser createCSVParser(Template template, PrefSettings prefSettings) {
        switch (template.getCode()) {
            case MAGDRONE:
                return new MagDroneCsvParser(template, prefSettings);

            case NMEA:
                return new NmeaCsvParser(template, prefSettings);

            default:
                return new CsvParser(template, prefSettings);
        }
    }
}
