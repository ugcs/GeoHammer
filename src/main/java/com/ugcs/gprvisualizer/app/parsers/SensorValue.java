package com.ugcs.gprvisualizer.app.parsers;

public record SensorValue(String header, Number data, Number originalData) {
    
    public SensorValue(String header, Number data) {
        this(header, data, data);
    }

    public SensorValue(SensorValue other) {
        this(other.header, other.data, other.originalData);
    }

    public SensorValue withValue(Number data) {
        return new SensorValue(header, data, originalData);
    }
}