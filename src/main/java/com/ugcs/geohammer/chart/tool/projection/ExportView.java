package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.model.ExportFormat;
import com.ugcs.geohammer.chart.tool.projection.model.ExportOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ExportScope;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

@Component
public class ExportView extends VBox {

    private static final double LABEL_WIDTH = 60;

    private static final double SPACING = 8;

    public static final ButtonType EXPORT = new ButtonType("Export", ButtonBar.ButtonData.OK_DONE);

    private final ProjectionModel projectionModel;

    private final ExportController exportController;

    private final ThemeService themeService;

    public ExportView(ProjectionModel projectionModel, ExportController exportController, ThemeService themeService) {
        this.projectionModel = projectionModel;
        this.exportController = exportController;
        this.themeService = themeService;

        setSpacing(SPACING);
        setPrefSize(420, 120);

        ExportOptions exportOptions = projectionModel.getExportOptions();

        // path
        Label pathLabel = new Label("Export to");
        pathLabel.setPrefWidth(LABEL_WIDTH);

        TextField path = new TextField();
        path.textProperty().bindBidirectional(exportOptions.pathProperty());
        HBox.setHgrow(path, Priority.ALWAYS);

        Button browseButton = new Button("…");
        browseButton.setPrefWidth(28);
        browseButton.setOnAction(e -> selectPath(path.getScene().getWindow()));

        HBox pathRow = new HBox(SPACING, pathLabel, path, browseButton);
        pathRow.setAlignment(Pos.BASELINE_LEFT);
        bindCollapsible(pathRow, exportOptions.exportingProperty().not());

        // scope
        Label scopeLabel = new Label("Scope");
        scopeLabel.setPrefWidth(LABEL_WIDTH);

        ComboBox<ExportScope> scopeSelector = new ComboBox<>(
                FXCollections.observableArrayList(List.of(ExportScope.values())));
        scopeSelector.valueProperty().bindBidirectional(exportOptions.scopeProperty());
        scopeSelector.setPrefWidth(140);

        HBox scopeRow = new HBox(SPACING, scopeLabel, scopeSelector);
        scopeRow.setAlignment(Pos.BASELINE_LEFT);
        bindCollapsible(scopeRow, exportOptions.exportingProperty().not());

        // progress
        Label progressLabel = new Label("Exporting");
        progressLabel.setPrefWidth(LABEL_WIDTH);

        ProgressBar progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(3);
        progress.progressProperty().bind(exportOptions.progressProperty());
        HBox.setHgrow(progress, Priority.ALWAYS);

        Button cancel = new Button("Cancel");
        cancel.setOnAction(event -> exportController.cancelExport());

        HBox progressRow = new HBox(SPACING, progressLabel, progress, cancel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        bindCollapsible(progressRow, exportOptions.exportingProperty());

        getChildren().addAll(
                pathRow,
                scopeRow,
                progressRow);
    }

    private static void bindCollapsible(Node node, ObservableValue<Boolean> visible) {
        node.visibleProperty().bind(visible);
        node.managedProperty().bind(node.visibleProperty());
    }

    public void show() {
        ExportOptions exportOptions = projectionModel.getExportOptions();

        initPath();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Export 3D Grid");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setPadding(new Insets(4));
        dialogPane.setContent(this);
        dialogPane.getButtonTypes().setAll(EXPORT, ButtonType.CLOSE);

        Stage stage = (Stage) dialogPane.getScene().getWindow();
        stage.setMinWidth(360);
        stage.setMinHeight(180);
        stage.setResizable(true);

        Button export = (Button)dialogPane.lookupButton(EXPORT);
        export.setDefaultButton(true);
        export.disableProperty().bind(exportOptions.exportingProperty().or(exportOptions.pathProperty().isEmpty()));
        export.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            export(dialog);
        });

        // close on ESC
        dialogPane.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close();
                e.consume();
            }
        });

        // cancel export on hide
        dialog.setOnHidden(event -> exportController.cancelExport());

        themeService.registerScene(dialogPane.getScene(), false);
        dialog.show();
    }

    private void initPath() {
        ExportOptions exportOptions = projectionModel.getExportOptions();
        TraceFile traceFile = projectionModel.getSelection().getFile();
        if (traceFile == null || traceFile.getFile() == null) {
            exportOptions.pathProperty().set(Strings.empty());
            return;
        }
        File source = traceFile.getFile();
        String baseName = FileNames.removeExtension(source.getName());
        File parent = source.getParentFile();
        String fileName = baseName + "." + ExportFormat.defaultFormat().getExtension();
        File target = parent != null
                ? new File(parent, fileName)
                : new File(fileName);
        exportOptions.pathProperty().set(target.getAbsolutePath());
    }

    private void selectPath(Window owner) {
        ExportOptions exportOptions = projectionModel.getExportOptions();
        ExportFormat pathFormat = ExportFormat.of(exportOptions.getPath());
        if (pathFormat == null) {
            pathFormat = ExportFormat.defaultFormat();
        }

        FileChooser fileSelector = new FileChooser();
        FileChooser.ExtensionFilter selectedFilter = null;
        for (ExportFormat format : ExportFormat.values()) {
            String pattern = "*." + format.getExtension();
            FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter(
                    format.getDisplayName() + " (" + pattern + ")", pattern);
            fileSelector.getExtensionFilters().add(filter);
            if (format == pathFormat) {
                selectedFilter = filter;
            }
        }
        if (selectedFilter != null) {
            fileSelector.setSelectedExtensionFilter(selectedFilter);
        }

        String initPath = exportOptions.getPath();
        if (!Strings.isNullOrBlank(initPath)) {
            File initFile = new File(initPath);
            File parent = initFile.getParentFile();
            if (parent != null && parent.isDirectory()) {
                fileSelector.setInitialDirectory(parent);
            }
            fileSelector.setInitialFileName(FileNames.removeExtension(initFile.getName()));
        }
        File selectedFile = fileSelector.showSaveDialog(owner);
        if (selectedFile != null) {
            exportOptions.pathProperty().set(selectedFile.getAbsolutePath());
        }
    }

    private void export(Dialog<Void> dialog) {
        ExportOptions exportOptions = projectionModel.getExportOptions();

        String path = exportOptions.getPath();
        if (Strings.isNullOrBlank(path)) {
            return;
        }
        ExportScope scope = exportOptions.getScope();
        if (scope == null) {
            return;
        }
        CompletableFuture<Void> exportTask = exportController.exportGrid(Path.of(path), scope);
        exportTask.whenComplete((result, t) -> {
            if (t == null) {
                Platform.runLater(dialog::close);
            } else if (!(t instanceof CancellationException)) {
                Dialogs.showError("Export failed", t);
            }
        });
    }
}
