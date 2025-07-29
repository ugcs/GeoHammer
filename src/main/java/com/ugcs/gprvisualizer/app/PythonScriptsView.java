package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PythonScriptsView extends VBox {

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

        ComboBox<String> scriptSelector = new ComboBox<>();
        scriptSelector.setPromptText("Select Python script");
        scriptSelector.setMaxWidth(Double.MAX_VALUE);

        // TODO: 28. 7. 2025. Remove mock
        List<PythonScript> scripts = List.of(
                new PythonScript("quspin_compensation.py", "QuSpin heading error compensation",
                        List.of(new PythonScriptParameter("threshold", "Error threshold",
                                PythonScriptParameter.ParameterType.DOUBLE, "0.1", true))),
                new PythonScript("noise_filter.py", "Advanced noise filtering",
                        List.of(new PythonScriptParameter("window_size", "Window size",
                                        PythonScriptParameter.ParameterType.INTEGER, "10", true),
                                new PythonScriptParameter("apply_smoothing", "Apply smoothing",
                                        PythonScriptParameter.ParameterType.BOOLEAN, "true", false)))
        );

        scriptSelector.getItems().addAll(
                scripts.stream()
                        .map(PythonScript::fileName)
                        .toList()
        );

        VBox parametersBox = new VBox(OptionPane.DEFAULT_SPACING);
        parametersBox.setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

        Label parametersLabel = new Label("Parameters:");
        parametersLabel.setStyle("-fx-font-weight: bold;");
        parametersLabel.setVisible(false);

        Button applyButton = new Button("Apply");
        applyButton.setDisable(true);

        scriptSelector.setOnAction(e -> {
            String filename = scriptSelector.getValue();
            PythonScript selected = scripts.stream()
                    .filter(script -> script.fileName.equals(filename))
                    .findFirst()
                    .orElse(null);

            parametersBox.getChildren().clear();

            if (selected != null) {
                parametersLabel.setVisible(true);
                parametersBox.getChildren().add(parametersLabel);

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
            String filename = scriptSelector.getValue();
            PythonScript selected = scripts.stream()
                    .filter(script -> script.fileName.equals(filename))
                    .findFirst()
                    .orElse(null);
            executeScript(selected, parametersBox, progressIndicator, applyButton);
        });

        VBox contentBox = new VBox();
        contentBox.getChildren().addAll(scriptSelector, parametersBox, applyButton);

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

    private void executeScript(@Nullable PythonScript script, VBox parametersBox,
                               ProgressIndicator progressIndicator, Button executeButton) {
        if (script == null) return;

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
                    dialog.setContentText("Python script '" + script.displayName() + "' executed successfully.");
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

    public record PythonScript(
            String fileName,
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
