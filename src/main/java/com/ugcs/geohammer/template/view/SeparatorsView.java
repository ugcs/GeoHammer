package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.template.model.FormatModel;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.ReentranceGuard;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeparatorsView extends HBox {

    private static final Map<String, String> SEPARATORS;

    static {
        SEPARATORS = new LinkedHashMap<>();
        SEPARATORS.put(",", ",");
        SEPARATORS.put(";", ";");
        SEPARATORS.put("\\t", "Tab");
        SEPARATORS.put(" ", "Space");
    }

    private final TemplateModel templateModel;

    private final List<ToggleButton> separatorToggles = new ArrayList<>();

    private final TextField customSeparatorInput = new TextField();

    private final ReentranceGuard separatorsGuard = new ReentranceGuard();

    public SeparatorsView(TemplateModel templateModel) {
        this.templateModel = templateModel;

        FormatModel format = templateModel.getFormat();

        SEPARATORS.forEach((separator, name) -> {
            ToggleButton separatorToggle = createSeparatorToggle(separator, name);
            separatorToggles.add(separatorToggle);
        });

        customSeparatorInput.setPromptText("Custom");
        customSeparatorInput.setPrefWidth(76);
        customSeparatorInput.setMinWidth(76);
        customSeparatorInput.textProperty().addListener((observable, oldValue, newValue) -> {
            if (separatorsGuard.isLatched()) {
                return;
            }
            removeSeparator(oldValue);
            addSeparator(newValue);
        });

        CheckBox repeatable = new CheckBox("Repeats");
        repeatable.setMinWidth(Region.USE_PREF_SIZE);
        repeatable.selectedProperty().bindBidirectional(format.repeatableSeparatorProperty());

        format.getSeparators().addListener((ListChangeListener<String>) change -> separatorsGuard.run(this::updateSeparators));
        separatorsGuard.run(this::updateSeparators);

        setSpacing(TemplateEditorView.HORIZONTAL_SPACING);
        setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(separatorToggles);
        getChildren().add(customSeparatorInput);
        getChildren().add(repeatable);
    }

    private ToggleButton createSeparatorToggle(String separator, String name) {
        FormatModel format = templateModel.getFormat();

        ToggleButton toggle = new ToggleButton(name);
        toggle.setUserData(separator);
        toggle.setMinWidth(34);
        toggle.setSelected(format.getSeparators().contains(separator));

        Listeners.onChange(toggle.selectedProperty(), selected -> {
            if (separatorsGuard.isLatched()) {
                return;
            }
            if (Boolean.TRUE.equals(selected)) {
                addSeparator(separator);
            } else {
                removeSeparator(separator);
            }
        });

        return toggle;
    }

    private void updateSeparators() {
        List<String> separators = templateModel.getFormat().getSeparators();
        for (ToggleButton toggle : separatorToggles) {
            toggle.setSelected(separators.contains((String)toggle.getUserData()));
        }
        String customSeparator = Strings.empty();
        for (String separator : separators) {
            if (!SEPARATORS.containsKey(separator)) {
                customSeparator = separator;
                break;
            }
        }
        customSeparatorInput.setText(customSeparator);
    }

    private void addSeparator(String separator) {
        List<String> separators = templateModel.getFormat().getSeparators();
        if (!Strings.isNullOrEmpty(separator) && !separators.contains(separator)) {
            separators.add(separator);
        }
    }

    private void removeSeparator(String separator) {
        if (!Strings.isNullOrEmpty(separator)) {
            List<String> separators = templateModel.getFormat().getSeparators();
            separators.remove(separator);
        }
    }
}
