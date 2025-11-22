package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.ScriptExecutionView;
import com.ugcs.geohammer.chart.csv.SeriesSelectorView;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.service.script.ScriptExecutor;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.status.Status;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CsvTab extends Tab {

    private final Model model;

    private ScriptExecutor scriptExecutor;

    // state

    private CsvFile selectedFile;

    // view

    private final Status status;

    private ToggleButton griddingToggle;

    // tools

    private final SeriesSelectorView seriesSelectorView;

    private final StatisticsView statisticsView;

    private final LowPassView lowPassView;

    private final GriddingView griddingView;

    private final TimeLagView timeLagView;

    private final RunningMedianView runningMedianView;

    private final QualityControlView qualityControlView;

    private final ScriptExecutionView scriptExecutionView;

    public CsvTab(
            Model model,
            ScriptExecutor scriptExecutor,
            Status status,
            SeriesSelectorView seriesSelectorView,
            StatisticsView statisticsView,
            LowPassView lowPassView,
            GriddingView griddingView,
            TimeLagView timeLagView,
            RunningMedianView runningMedianView,
            QualityControlView qualityControlView
    ) {
        this.model = model;
        this.scriptExecutor = scriptExecutor;
        this.status = status;

        this.seriesSelectorView = seriesSelectorView;
        this.statisticsView = statisticsView;
        this.lowPassView = lowPassView;
        this.griddingView = griddingView;
        this.timeLagView = timeLagView;
        this.runningMedianView = runningMedianView;
        this.qualityControlView = qualityControlView;

        // TODO
//        qualityControlToggle.addEventHandler(ActionEvent.ACTION, event ->
//                toggleQualityLayer(qualityControlToggle.isSelected()));
//        if (qualityLayer.isActive() == active) {
//            return;
//        }
//        qualityLayer.setActive(active);
//        model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
        // ...

        StackPane seriesPane = new StackPane(seriesSelectorView);
        seriesPane.setPadding(new Insets(10, 16, 10, 16));
        seriesPane.setStyle("-fx-background-color: #666666;");

        scriptExecutionView = new ScriptExecutionView(model, status, selectedFile, scriptExecutor);
        StackPane scriptsPane = new StackPane(scriptExecutionView);

        ToggleButton statisticsToggle = Tools.createToggle("Statistics", statisticsView);
        ToggleButton lowPassToggle = Tools.createToggle("Low-pass filter", lowPassView);
        griddingToggle = Tools.createToggle("Gridding", griddingView);
        ToggleButton timeLagToggle = Tools.createToggle("GNSS time-lag", timeLagView);
        ToggleButton runningMedianToggle = Tools.createToggle("Running median filter", runningMedianView);
        ToggleButton qualityControlToggle = Tools.createToggle("Quality control", qualityControlView);
        ToggleButton scriptsToggle = Tools.createToggle("Scripts", scriptsPane);

//        StatisticsView statisticsView2 = AppContext.getInstance(StatisticsView.class);
//        ToggleButton statisticsToggle2 =  Tools.createToggle("Statistics 2", statisticsView2);

        VBox container = new VBox(Tools.DEFAULT_SPACING,
                statisticsToggle,
                statisticsView,
//                statisticsToggle2,
//                statisticsView2,
                lowPassToggle,
                lowPassView,
                griddingToggle,
                griddingView,
                timeLagToggle,
                timeLagView,
                runningMedianToggle,
                runningMedianView,
                qualityControlToggle,
                qualityControlView,
                scriptsToggle,
                scriptsPane);
        container.setPadding(new Insets(10, 8, 10, 8));

        ScrollPane scrollContainer = Tools.createVerticalScrollContainer(container);
        scrollContainer.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                // redirect focus to a tab pane
                Platform.runLater(() -> getTabPane().requestFocus());
            }
        });

        VBox tabContainer = new VBox(seriesPane, scrollContainer);
        setContent(tabContainer);
    }

    @EventListener
    private void handleFileSelectedEvent(FileSelectedEvent event) {
        selectedFile = event.getFile() instanceof CsvFile csvFile
                ? csvFile
                : null;

        if (selectedFile != null) {
            if (scriptExecutionView != null) {
                scriptExecutionView.updateView(event.getFile());
            }
        }
    }

    // gridding

    // TODO deprecated
    public void griddingProgress(boolean inProgress) {
        Platform.runLater(() -> {
            griddingView.showProgress(inProgress);
            griddingView.disableInput(inProgress);
            griddingView.disableActions(inProgress);
        });
    }

    // TODO deprecated
    public GriddingRange getGriddingRange(CsvFile csvFile, String seriesName) {
        return griddingView.getGriddingRange(csvFile, seriesName);
    }

    // TODO deprecated
    public ToggleButton getGridding() {
        return griddingToggle;
    }
}
