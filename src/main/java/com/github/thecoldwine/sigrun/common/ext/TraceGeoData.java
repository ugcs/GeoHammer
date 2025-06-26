package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.SensorValue;

import java.util.ArrayList;
import java.util.List;

public class TraceGeoData extends GeoData {

    private int traceIndex;

    private static List<SensorValue> newSensorValues() {
        return new ArrayList<>();
    }

    private static GeoCoordinates newGeoCoordinates(double latitude, double longitude) {
        return new GeoCoordinates(
                latitude,
                longitude,
                null,
                null,
                0,
                null
        );
    }

    public TraceGeoData(int traceIndex) {
        super(
                false,
                0,
                newSensorValues(),
                newGeoCoordinates(0, 0)

        );

        this.traceIndex = traceIndex;
    }

    public int getTraceIndex() {
        return traceIndex;
    }

    public void setTraceIndex(int traceIndex) {
        this.traceIndex = traceIndex;
    }
}
