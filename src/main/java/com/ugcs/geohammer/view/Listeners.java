package com.ugcs.geohammer.view;

import javafx.beans.value.ObservableValue;

import java.util.Objects;
import java.util.function.Consumer;

public final class Listeners {

    private Listeners() {
    }

    public static <T> void onChange(ObservableValue<T> property, Consumer<T> consumer) {
        if (property != null && consumer != null) {
            property.addListener((observable, oldValue, newValue) -> {
                if (!Objects.equals(oldValue, newValue)) {
                    consumer.accept(newValue);
                }
            });
        }
    }
}
