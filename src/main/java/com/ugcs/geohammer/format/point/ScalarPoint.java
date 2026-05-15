package com.ugcs.geohammer.format.point;

public record ScalarPoint(double x, double y, double z, float value) {

    public static final int BYTES = 3 * Double.BYTES + Float.BYTES;

    public record Range(ScalarPoint min, ScalarPoint max) {
    }
}
