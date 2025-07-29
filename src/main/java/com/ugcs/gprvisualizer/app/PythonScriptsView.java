package com.ugcs.gprvisualizer.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.scripts.JsonScriptMetadataMetadataLoader;
import com.ugcs.gprvisualizer.app.scripts.PythonScriptMetadataLoader;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PythonScriptsView extends VBox {

    private static final Logger log = LoggerFactory.getLogger(PythonScriptsView.class);
    private final Model model;
    private final ExecutorService executor;
    private final SgyFile selectedFile;

    public PythonScriptsView(Model model, SgyFile selectedFile) {
        this.model = model;
        this.selectedFile = selectedFile;

        this.executor = Executors.newSingleThreadExecutor();

        setSpacing(OptionPane.DEFAULT_SPACING);
        setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);

        ComboBox<String> scriptsMetadataSelector = new ComboBox<>();
        scriptsMetadataSelector.setPromptText("Select Python script");
        scriptsMetadataSelector.setMaxWidth(Double.MAX_VALUE);

        PythonScriptMetadataLoader scriptsMetadataLoader = new JsonScriptMetadataMetadataLoader();
        List<PythonScriptMetadata> loadedScriptsMetadata;
        try {
            loadedScriptsMetadata = scriptsMetadataLoader.loadScriptsMetadata(Path.of("scripts"));
        } catch (IOException e) {
            log.warn("Failed to load Python scripts", e);
            loadedScriptsMetadata = List.of();
        }
        final List<PythonScriptMetadata> scriptsMetadata = loadedScriptsMetadata;

        scriptsMetadataSelector.getItems().addAll(
                scriptsMetadata.stream()
                        .map(PythonScriptMetadata::filename)
                        .toList()
        );

        VBox parametersBox = new VBox(OptionPane.DEFAULT_SPACING);
        parametersBox.setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

        Label parametersLabel = new Label("Parameters:");
        parametersLabel.setStyle("-fx-font-weight: bold;");
        parametersLabel.setVisible(false);

        Button applyButton = new Button("Apply");
        applyButton.setDisable(true);

        scriptsMetadataSelector.setOnAction(e -> {
            String filename = scriptsMetadataSelector.getValue();
            PythonScriptMetadata selected = scriptsMetadata.stream()
                    .filter(scriptMetadata -> scriptMetadata.filename.equals(filename))
                    .findFirst()
                    .orElse(null);

            parametersBox.getChildren().clear();

            if (selected != null) {
                if (selected.parameters().isEmpty()) {
                    parametersLabel.setVisible(false);
                } else {
                    parametersLabel.setVisible(true);
                    parametersBox.getChildren().add(parametersLabel);
                }

                for (PythonScriptParameter param : selected.parameters()) {
                    VBox paramBox = createParameterInput(param);
                    parametersBox.getChildren().add(paramBox);
                }

                applyButton.setDisable(false);
            } else {
                parametersLabel.setVisible(false);
                applyButton.setDisable(true);
            }
        });

        applyButton.setOnAction(event -> {
            String filename = scriptsMetadataSelector.getValue();
            PythonScriptMetadata selected = scriptsMetadata.stream()
                    .filter(scriptMetadata -> scriptMetadata.filename.equals(filename))
                    .findFirst()
                    .orElse(null);
            executeScript(selected, parametersBox, progressIndicator, applyButton);
        });

        VBox contentBox = new VBox();
        contentBox.getChildren().addAll(scriptsMetadataSelector, parametersBox, applyButton);

        StackPane stackPane = new StackPane(contentBox, progressIndicator);
        StackPane.setAlignment(progressIndicator, Pos.CENTER);

        getChildren().add(stackPane);

        setVisible(false);
        setManaged(false);
    }

    private VBox createParameterInput(PythonScriptParameter param) {
        VBox paramBox = new VBox(5);

        Label label = new Label(param.displayName() + (param.required() ? " *" : ""));

        Node inputNode = switch (param.type()) {
            case STRING, FILE_PATH -> {
                TextField textField = new TextField(param.defaultValue());
                textField.setPromptText(param.displayName());
                yield textField;
            }
            case INTEGER -> {
                TextField textField = new TextField(param.defaultValue());
                textField.setPromptText("Enter integer value");
                yield textField;
            }
            case DOUBLE -> {
                TextField textField = new TextField(param.defaultValue());
                textField.setPromptText("Enter decimal value");
                yield textField;
            }
            case BOOLEAN -> {
                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(Boolean.parseBoolean(param.defaultValue()));
                yield checkBox;
            }
        };

        inputNode.setUserData(param.name());
        paramBox.getChildren().addAll(label, inputNode);

        return paramBox;
    }

    private void executeScript(PythonScriptsView.@Nullable PythonScriptMetadata scriptMetadata, VBox parametersBox,
							   ProgressIndicator progressIndicator, Button executeButton) {
        if (scriptMetadata == null) return;

        // TODO: 29. 7. 2025. In feature need to send this params to python script
        Map<String, String> parameters = new HashMap<>();
        for (Node node : parametersBox.getChildren()) {
            if (node instanceof VBox paramBox) {
                for (Node child : paramBox.getChildren()) {
                    if (child.getUserData() != null) {
                        String paramName = (String) child.getUserData();
                        String value = extractValueFromNode(child);
                        parameters.put(paramName, value);
                    }
                }
            }
        }

        progressIndicator.setVisible(true);
        progressIndicator.setManaged(true);
        parametersBox.setDisable(true);
        executeButton.setDisable(true);

        executor.submit(() -> {
            try {
                // TODO: 29. 7. 2025. Remove after implementing real script execution
                Thread.sleep(2000);

                Platform.runLater(() -> {
                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Script Execution");
                    dialog.setHeaderText("Success");
                    dialog.setContentText("Python script '" + scriptMetadata.displayName() + "' executed successfully.");
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                    dialog.showAndWait();

                    // TODO: 29. 7. 2025. add other files check (sgy, dzt)
                    if (selectedFile instanceof CsvFile) {
                        model.publishEvent(new WhatChanged(this, WhatChanged.Change.csvDataFiltered));
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Dialog<String> dialog = new Dialog<>();
                    dialog.setTitle("Script Execution Error");
                    dialog.setHeaderText("Error");
                    dialog.setContentText("Failed to execute script: " + ex.getMessage());
                    dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
                    dialog.showAndWait();
                });
            } finally {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    progressIndicator.setManaged(false);
                    parametersBox.setDisable(false);
                    executeButton.setDisable(false);
                });
            }
        });
    }

    private String extractValueFromNode(Node node) {
        return switch (node) {
            case TextField textField -> textField.getText();
            case CheckBox checkBox -> String.valueOf(checkBox.isSelected());
            default -> "";
        };
    }

    public record PythonScriptMetadata(
            String filename,
            @JsonProperty("display_name")
            String displayName,
            List<PythonScriptParameter> parameters
    ) {
    }

    public record PythonScriptParameter(
            String name,
            String displayName,
            ParameterType type,
            String defaultValue,
            boolean required
    ) {
        public enum ParameterType {
            STRING, INTEGER, DOUBLE, BOOLEAN, FILE_PATH
        }
    }
}
