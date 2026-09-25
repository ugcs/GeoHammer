package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.chart.gpr.GPRChart;
import com.ugcs.geohammer.chart.gpr.ProfileSettings;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.service.gpr.CommandRegistry;
import com.ugcs.geohammer.service.gpr.SpreadCoordinates;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.control.BaseSlider;
import com.ugcs.geohammer.view.control.SelectorWithLabel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class GprBackgroundTool extends FilterToolView {

    private final Model model;

    private final UndoModel undoModel;

    private final CommandRegistry commandRegistry;

    private final ColorScaleSelector colorScaleSelector;

    private final ContrastSlider contrastSlider;

    private final GainSlider gainSlider;

    private final Button spreadCoordinates;

    private final Button removeBackground;

    private GPRChart selectedChart;

    public GprBackgroundTool(
            Model model,
            UndoModel undoModel,
            CommandRegistry commandRegistry,
            ExecutorService executor
    ) {
        super(executor);

        this.model = model;
        this.undoModel = undoModel;
        this.commandRegistry = commandRegistry;

        colorScaleSelector = new ColorScaleSelector();
        contrastSlider = new ContrastSlider();
        gainSlider = new GainSlider();

        spreadCoordinates = new Button("Spread coordinates");
        spreadCoordinates.setOnAction(event -> spreadCoordinates());
        spreadCoordinates.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(spreadCoordinates, Priority.ALWAYS);

        removeBackground = new Button("Remove background");
        removeBackground.setOnAction(event -> removeBackground());
        removeBackground.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(removeBackground, Priority.ALWAYS);

        HBox buttons = new HBox(Views.DEFAULT_SPACING,
                removeBackground,
                spreadCoordinates);
        inputContainer.getChildren().addAll(colorScaleSelector, contrastSlider, gainSlider, buttons);

        // disable base filter buttons
        buttonContainer.setVisible(false);
        buttonContainer.setManaged(false);
    }

    private void spreadCoordinates() {
        submitAction(() -> {
            commandRegistry.runForGprFiles(
                    model.getFileManager().getGprFiles(),
                    new SpreadCoordinates());
            Platform.runLater(this::updateView);
            return null;
        });
    }

    private void removeBackground() {
        submitAction(() -> {
            for (TraceFile traceFile : model.getFileManager().getGprFiles()) {
                if (!traceFile.isBackgroundRemoved()) {
                    traceFile.removeBackground(undoModel);
                }
            }
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
            return null;
        });
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof TraceFile;
    }

    @Override
    public void updateView() {
        spreadCoordinates.setDisable(!model.isSpreadCoordinatesNecessary());

        colorScaleSelector.update();
        contrastSlider.update();
        gainSlider.update();
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        selectedChart = event.getFile() instanceof TraceFile traceFile
                ? model.getGprChart(traceFile)
                : null;

        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onFileOpened(FileOpenedEvent event) {
        Platform.runLater(this::updateView);
    }

    @EventListener
    private void onSomethingChanged(WhatChanged changed) {
        if (changed.isUpdateButtons() || changed.isTraceCut()) {
            Platform.runLater(this::updateView);
        }
    }

    class ColorScaleSelector extends SelectorWithLabel<SpectrumType> {

        public ColorScaleSelector() {
            super("Color scale", List.of(SpectrumType.values()));

            setSpacing(2.5);
            setPadding(new Insets(4, 8, 4, 0));
            setPrefWidth(Double.MAX_VALUE);

            getSelector().setPrefWidth(200);
            getSelector().setValue(SpectrumType.GRAYSCALE);
            Listeners.onChange(getSelector().valueProperty(), this::onValueChanged);

            update();
        }

        public void onValueChanged(SpectrumType colorScale) {
            GPRChart chart = selectedChart;
            if (chart != null && colorScale != null) {
                chart.getField().getSettings().setColorScale(colorScale);
                chart.repaint();
            }
        }

        public void update() {
            GPRChart chart = selectedChart;
            if (chart != null) {
                SpectrumType colorScale = chart.getField().getSettings().getColorScale();
                getSelector().setValue(colorScale != null ? colorScale : SpectrumType.GRAYSCALE);
            }
        }
    }

    class ContrastSlider extends BaseSlider {

        public ContrastSlider() {
            super("Contrast", Unit.empty(),
                    new Range(ProfileSettings.MIN_CONTRAST, ProfileSettings.MAX_CONTRAST), 25);
            update();
        }

        @Override
        public void onValueChanged(Number value) {
            GPRChart chart = selectedChart;
            if (chart != null && value != null) {
                chart.getField().getSettings().setContrast(value.doubleValue());
                chart.repaint();
            }
        }

        @Override
        public void update() {
            GPRChart chart = selectedChart;
            if (chart != null) {
                slider.setValue(chart.getField().getSettings().getContrast());
            }
        }
    }

    class GainSlider extends BaseSlider {

        public GainSlider() {
            super("Gain at max depth", Unit.symbol("dB"),
                    new Range(ProfileSettings.MIN_MAX_GAIN, ProfileSettings.MAX_MAX_GAIN), 32);
            update();
        }

        @Override
        public void onValueChanged(Number value) {
            GPRChart chart = selectedChart;
            if (chart != null && value != null) {
                chart.getField().getSettings().setMaxGain(value.doubleValue());
                chart.repaint();
            }
        }

        @Override
        public void update() {
            GPRChart chart = selectedChart;
            if (chart != null) {
                slider.setValue(chart.getField().getSettings().getMaxGain());
            }
        }
    }
}
