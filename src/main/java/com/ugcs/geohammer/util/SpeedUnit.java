package com.ugcs.geohammer.util;

public enum SpeedUnit {

    METERS_PER_SECOND {

        @Override
        public double toMetersPerSecond(double speed) {
            return speed;
        }

        @Override
        public double fromMetersPerSecond(double speed) {
            return speed;
        }
    },

    KNOTS {

        @Override
        public double toMetersPerSecond(double speed) {
            return speed * MPS_PER_KNOT;
        }

        @Override
        public double fromMetersPerSecond(double speed) {
            return speed / MPS_PER_KNOT;
        }
    };

    // m/s = MPS_PER_KNOT * knots; 1 knot = 1852 m / 3600 s
    private static final double MPS_PER_KNOT = 1852.0 / 3600.0;

    public abstract double toMetersPerSecond(double speed);

    public abstract double fromMetersPerSecond(double speed);

    public double convert(double speed, SpeedUnit to) {
        return to.fromMetersPerSecond(toMetersPerSecond(speed));
    }
}
