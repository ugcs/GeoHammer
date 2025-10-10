package com.ugcs.gprvisualizer.math;

public record DataPoint(double latitude, double longitude, double value) implements Comparable<DataPoint> {

    public DataPoint {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be in range [-90, 90]");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be in range [-180, 180]");
        }
    }

    @Override
    public int compareTo(DataPoint o) {
        return latitude == o.latitude ? Double.compare(longitude, o.longitude) : Double.compare(latitude, o.latitude);
    }
}
