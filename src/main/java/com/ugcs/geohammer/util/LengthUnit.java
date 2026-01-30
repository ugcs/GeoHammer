package com.ugcs.geohammer.util;

public enum LengthUnit {

    METER {

        @Override
        public double toMeters(double length) {
            return length;
        }

        @Override
        public double fromMeters(double length) {
            return length;
        }
    },

    FOOT {

        @Override
        public double toMeters(double length) {
            return length * METERS_PER_FOOT;
        }

        @Override
        public double fromMeters(double length) {
            return length / METERS_PER_FOOT;
        }
    };

    // meters = METERS_PER_FOOT * feet
    private static final double METERS_PER_FOOT = 0.3048;

    public abstract double toMeters(double length);

    public abstract double fromMeters(double length);

    public double convert(double length, LengthUnit to) {
        return to.fromMeters(toMeters(length));
    }
}
