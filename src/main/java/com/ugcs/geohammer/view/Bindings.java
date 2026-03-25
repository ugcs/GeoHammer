package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Check;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

public final class Bindings {

    private Bindings() {
    }

    public static DoubleProperty metersToCentimeters(DoubleProperty meters) {
        Check.notNull(meters);

        DoubleProperty centimeters = new SimpleDoubleProperty(meters.get() * 100);
        Listeners.onChange(centimeters, v -> meters.set(v.doubleValue() / 100));
        Listeners.onChange(meters, v -> centimeters.set(v.doubleValue() * 100));
        return centimeters;
    }
}
