package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.template.TemplateEditorController;
import com.ugcs.geohammer.template.model.FileSample;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Toggles;
import com.ugcs.geohammer.view.Views;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class SampleView extends VBox {

    private final TemplateModel templateModel;

    private final TemplateEditorController templateEditorController;

    private final ToggleButton rawMode = new ToggleButton("Raw");

    private final ToggleButton parsedMode = new ToggleButton("Parsed");

    private final Label fileName = new Label();

    private final FileSampleView rawView;

    private final ParsedSampleView parsedView;

    public SampleView(
            TemplateModel templateModel,
            TemplateEditorController templateEditorController,
            FileSampleView rawView,
            ParsedSampleView parsedView) {
        this.templateModel = templateModel;
        this.templateEditorController = templateEditorController;
        this.rawView = rawView;
        this.parsedView = parsedView;

        StackPane content = new StackPane(rawView, parsedView);
        bindVisible(rawView, rawMode);
        bindVisible(parsedView, parsedMode);

        setSpacing(Views.DEFAULT_SPACING);
        getChildren().addAll(content, createModeRow());
        VBox.setVgrow(content, Priority.ALWAYS);

        Listeners.onChange(templateModel.fileSampleProperty(), this::updateSample);
        updateSample(templateModel.getFileSample());
    }

    private Node createModeRow() {
        ToggleGroup group = new ToggleGroup();
        rawMode.setToggleGroup(group);
        rawMode.setMinWidth(Region.USE_PREF_SIZE);
        parsedMode.setToggleGroup(group);
        parsedMode.setMinWidth(Region.USE_PREF_SIZE);
        parsedMode.setSelected(true);
        Toggles.keepOneSelected(group);

        Button useMatchLine = new Button("Match line");
        useMatchLine.setMinWidth(Region.USE_PREF_SIZE);
        useMatchLine.setOnAction(e -> templateEditorController.setMatchRegex(rawView.getSelectedLine()));
        useMatchLine.disableProperty().bind(
                rawView.getSelectionModel().selectedIndexProperty().lessThan(0));
        bindVisible(useMatchLine, rawMode);

        Button skipToLine = new Button("Skip to line");
        skipToLine.setMinWidth(Region.USE_PREF_SIZE);
        skipToLine.setOnAction(e -> templateEditorController.setSkipRegex(rawView.getSelectedLine()));
        skipToLine.disableProperty().bind(
                rawView.getSelectionModel().selectedIndexProperty().lessThan(0));
        bindVisible(skipToLine, rawMode);

        fileName.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        fileName.getStyleClass().addAll(Views.TOP_LABEL_STYLES);

        HBox row = new HBox(Views.DEFAULT_SPACING,
                rawMode, parsedMode, useMatchLine, skipToLine,
                createMatchChip(), createSkipChip(),
                Views.createSpacer(), fileName);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node createMatchChip() {
        Button chip = createChip("Custom match ✕", "Reset to the auto-built match regex");
        chip.setOnAction(e -> templateModel.autoMatchRegexProperty().set(true));
        bindVisible(chip, templateModel.autoMatchRegexProperty().not());
        return chip;
    }

    private Node createSkipChip() {
        Button chip = createChip("Custom skip ✕", "Do not skip lines");
        chip.setOnAction(e -> templateModel.getSkipLines().matchRegexProperty().set(Strings.empty()));
        bindVisible(chip, Bindings.createBooleanBinding(
                () -> !Strings.isNullOrBlank(templateModel.getSkipLines().getMatchRegex()),
                templateModel.getSkipLines().matchRegexProperty()));
        return chip;
    }

    private static Button createChip(String text, String tooltip) {
        Button chip = new Button(text);
        chip.getStyleClass().add("status-action");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setTooltip(new Tooltip(tooltip));
        return chip;
    }

    private static void bindVisible(Node node, ToggleButton mode) {
        bindVisible(node, mode.selectedProperty());
    }

    private static void bindVisible(Node node, ObservableValue<Boolean> visible) {
        node.visibleProperty().bind(visible);
        node.managedProperty().bind(node.visibleProperty());
    }

    private void updateSample(@Nullable FileSample fileSample) {
        fileName.setText(fileSample != null ? fileSample.file().getName() : "No context file");
        fileName.setTooltip(fileSample != null ? new Tooltip(fileSample.file().getAbsolutePath()) : null);
    }
}
