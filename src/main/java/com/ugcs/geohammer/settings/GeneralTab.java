package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.control.NodeWithLabel;
import com.ugcs.geohammer.view.style.Theme;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.validation.ValidationResult;
import org.springframework.stereotype.Component;

@Component
public class GeneralTab implements SettingsTab {

    private final Model model;

    private final ThemeService themeService;

    private final VBox content;

    private final ComboBox<Theme> themeSelector;

    private final TextField traceLookupThresholdInput;

    public GeneralTab(Model model, ThemeService themeService) {
        this.model = Check.notNull(model);
        this.themeService = Check.notNull(themeService);

        themeSelector = new ComboBox<>();
        themeSelector.getItems().addAll(Theme.values());
        NodeWithLabel<ComboBox<Theme>> themeSelectorWithLabel = new NodeWithLabel<>(
                "Theme", themeSelector, false);

        traceLookupThresholdInput = new TextField();
        traceLookupThresholdInput.setPrefWidth(60);
        NodeWithLabel<TextField> thresholdWithLabel = new NodeWithLabel<>(
                "Trace lookup threshold (m)", traceLookupThresholdInput, false);

        content = new VBox(SettingsView.VERTICAL_SPACING,
                new HBox(themeSelectorWithLabel, Views.createSpacer(), thresholdWithLabel));
    }

    @Override
    public Tab getTab() {
        return new Tab("General", content);
    }

    @Override
    public ValidationResult validate() {
        try {
            double threshold = Double.parseDouble(traceLookupThresholdInput.getText());
            if (threshold < 0) {
                return ValidationResult.fromError(
                        traceLookupThresholdInput,
                        "Trace lookup threshold must be a non-negative number");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.fromError(
                    traceLookupThresholdInput,
                    "Trace lookup threshold must be a valid number");
        }
        return new ValidationResult();
    }

    @Override
    public void load() {
        themeSelector.setValue(themeService.getTheme());
        traceLookupThresholdInput.setText(String.valueOf(model.getTraceLookupThreshold()));
    }

    @Override
    public void save() {
        Theme theme = themeSelector.getValue();
        themeService.setTheme(theme);
        try {
            double threshold = Double.parseDouble(traceLookupThresholdInput.getText());
            model.setTraceLookupThreshold(threshold);
        } catch (NumberFormatException ignore) {
        }
    }
}
