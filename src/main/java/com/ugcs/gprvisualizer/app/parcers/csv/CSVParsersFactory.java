package com.ugcs.gprvisualizer.app.parcers.csv;

import com.ugcs.gprvisualizer.app.yaml.Template;

public class CSVParsersFactory {

    private final String MAGDRONE = "magdrone";
    
    private final String NMEA = "nmea";

    public CsvParser createCSVParser(Template template) {
        switch (template.getCode()) {
            case MAGDRONE:
                return new MagDroneCsvParser(template);

            case NMEA:
                return new NmeaCsvParser(template);

            default:
                return new CsvParser(template);
        }
    }
}
