package com.ugcs.geohammer.view;

import javafx.application.Platform;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

public final class Toggles {

    private Toggles() {
    }

    // radio-like behavior: clicking a selected toggle does not clear a selection;
    // re-selection is deferred to avoid re-entrant toggle group updates
    public static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                Platform.runLater(() -> {
                    if (group.getSelectedToggle() == null) {
                        group.selectToggle(oldToggle);
                    }
                });
            }
        });
    }

    public static void select(ToggleGroup group, Object userData) {
        for (Toggle toggle : group.getToggles()) {
            if (toggle.getUserData() == userData) {
                toggle.setSelected(true);
                return;
            }
        }
    }
}
