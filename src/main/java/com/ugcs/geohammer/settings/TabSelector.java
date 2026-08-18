package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Listeners;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class TabSelector {

    private final ToggleGroup toggles = new ToggleGroup();

    private final ObjectProperty<Tab> selectedTab = new SimpleObjectProperty<>();

    public List<ToggleButton> getToggles() {
        ObservableList<Toggle> toggleNodes = toggles.getToggles();
        List<ToggleButton> toggleButtons = new ArrayList<>(toggleNodes.size());
        for (Toggle toggleNode : toggleNodes) {
            if (toggleNode instanceof ToggleButton toggleButton) {
                toggleButtons.add(toggleButton);
            }
        }
        return toggleButtons;
    }

    public Tab getSelectedTab() {
        return selectedTab.get();
    }

    public ObjectProperty<Tab> selectedTabProperty() {
        return selectedTab;
    }

    public void select(Tab tab) {
        if (tab == null) {
            return;
        }
        for (Toggle toggle : toggles.getToggles()) {
            if (Objects.equals(toggle.getUserData(), tab)) {
                toggle.setSelected(true);
                return;
            }
        }
    }

    public void addTab(Tab tab) {
        Check.notNull(tab);

        ToggleButton toggle = new ToggleButton(tab.title());
        toggle.getStyleClass().add("flat");
        toggle.setToggleGroup(toggles);
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setAlignment(Pos.CENTER_LEFT);
        toggle.setUserData(tab);

        toggle.setOnAction(event -> {
            if (!toggle.isSelected()) {
                toggle.setSelected(true);
            }
        });
        Listeners.onChange(toggle.selectedProperty(), selected -> {
            if (selected) {
                if (toggle.getUserData() instanceof Tab nodeTab) {
                    selectedTab.set(nodeTab);
                }
            }
        });
    }
}
