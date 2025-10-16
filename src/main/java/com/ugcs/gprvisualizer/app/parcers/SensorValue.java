package com.ugcs.gprvisualizer.app.parcers;

public record SensorValue(String header, String unit, Number data, Number originalData) {
    
    public SensorValue(String header, String unit, Number data) {
        this(header, unit, data, data);
    }

    public SensorValue(SensorValue other) {
        this(other.header, other.unit, other.data, other.originalData);
    }

    public SensorValue withValue(Number data) {
        return new SensorValue(header, unit, data, originalData);
    }
}