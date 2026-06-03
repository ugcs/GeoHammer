package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;

public final class NmeaSchema {

    public static final Column ALTITUDE_COLUMN = new Column("Ellipsoidal Height")
            .withUnit("m")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column COURSE_COLUMN = new Column("Course")
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column PITCH_COLUMN = new Column("Pitch")
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column ROLL_COLUMN = new Column("Roll")
            .withUnit("deg")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column SPEED_COLUMN = new Column("Speed")
            .withUnit("m/s")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column DEPTH_COLUMN = new Column("Depth")
            .withUnit("m")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column TRANSDUCER_OFFSET_COLUMN = new Column("Transducer Offset")
            .withUnit("m")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column DEPTH_HIGH_FREQUENCY_COLUMN = new Column("Depth High Frequency")
            .withUnit("m")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column DEPTH_LOW_FREQUENCY_COLUMN = new Column("Depth Low Frequency")
            .withUnit("m")
            .withDisplay(true)
            .withReadOnly(true);

    public static final Column TEMPERATURE_COLUMN = new Column("Temperature")
            .withUnit("deg C")
            .withDisplay(true)
            .withReadOnly(true);

    public static ColumnSchema createSchema() {
        ColumnSchema schema = ColumnSchema.copy(TraceGeoData.SCHEMA);
        return schema;
    }

    private NmeaSchema() {
    }
}
