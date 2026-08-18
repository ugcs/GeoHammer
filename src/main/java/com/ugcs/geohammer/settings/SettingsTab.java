package com.ugcs.geohammer.settings;

import org.controlsfx.validation.ValidationResult;

public interface SettingsTab {

    Tab getTab();

    default ValidationResult validate() {
        return new ValidationResult();
    }

    default void load() {
    }

    default void save() {
    }
}
