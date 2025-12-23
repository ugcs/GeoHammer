package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;

public final class SonarSchema {

    public static final String ALTITUDE_HEADER = "Ellipsoidal Height";

    public static final Column ALTITUDE_COLUMN = new Column(ALTITUDE_HEADER)
            .withUnit("m")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String DEPTH_HEADER = "Depth";

    public static final Column DEPTH_COLUMN = new Column(DEPTH_HEADER)
            .withUnit("m")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String VEHICLE_HEADING_HEADER = "Vehicle Heading";

    public static final Column VEHICLE_HEADING_COLUMN = new Column(VEHICLE_HEADING_HEADER)
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String TRANSDUCER_HEADING_HEADER = "Transducer Heading";

    public static final Column TRANSDUCER_HEADING_COLUMN = new Column(TRANSDUCER_HEADING_HEADER)
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String HEADING_HEADER = "Heading";

    public static final Column HEADING_COLUMN = new Column(HEADING_HEADER)
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String PITCH_HEADER = "Pitch";

    public static final Column PITCH_COLUMN = new Column(PITCH_HEADER)
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String ROLL_HEADER = "Roll";

    public static final Column ROLL_COLUMN = new Column(ROLL_HEADER)
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String TEMPERATURE_HEADER = "Temperature";

    public static final Column TEMPERATURE_COLUMN = new Column(TEMPERATURE_HEADER)
            .withUnit("deg C")
            .withDisplay(true)
            .withReadOnly(true);

    public static final String PRESSURE_HEADER = "Pressure";

    public static final Column PRESSURE_COLUMN = new Column(PRESSURE_HEADER)
            .withUnit("bar")
            .withDisplay(true)
            .withReadOnly(true);

    public static ColumnSchema createSchema() {
        // basic (min) schema of sonar file
        ColumnSchema schema = ColumnSchema.copy(TraceGeoData.SCHEMA);
        schema.addColumn(DEPTH_COLUMN);
        return schema;
    }

    private SonarSchema() {
    }
}
