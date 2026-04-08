package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Check;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.util.function.DoubleUnaryOperator;

public final class Bindings {

    private Bindings() {
    }

    public static DoubleProperty metersToCentimeters(DoubleProperty meters) {
        Check.notNull(meters);
        return bindMapped(meters, m -> m * 100, cm -> cm / 100);
    }

    public static DoubleProperty fractionToPercent(DoubleProperty fraction) {
        Check.notNull(fraction);
        return bindMapped(fraction, f -> f * 100, p -> p / 100);
    }

    private static DoubleProperty bindMapped(
            DoubleProperty source, DoubleUnaryOperator toTarget, DoubleUnaryOperator fromTarget) {
        DoubleProperty target = new SimpleDoubleProperty(toTarget.applyAsDouble(source.get()));

        Guard guard = new Guard();
        Listeners.onChange(source, v -> {
            if (!guard.updating) {
                guard.updating = true;
                try {
                    target.set(toTarget.applyAsDouble(v.doubleValue()));
                } finally {
                    guard.updating = false;
                }
            }
        });
        Listeners.onChange(target, v -> {
            if (!guard.updating) {
                guard.updating = true;
                try {
                    source.set(fromTarget.applyAsDouble(v.doubleValue()));
                } finally {
                    guard.updating = false;
                }
            }
        });
        return target;
    }

    private static class Guard {
        boolean updating;
    }
}
