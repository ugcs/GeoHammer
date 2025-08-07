package com.ugcs.gprvisualizer.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.app.scripts.JsonScriptMetadataLoader;
import com.ugcs.gprvisualizer.app.scripts.ScriptMetadataLoader;
import com.ugcs.gprvisualizer.app.service.PythonScriptExecutorService;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.FileTemplate;
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
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ScriptExecutionView extends VBox {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutionView.class);

	private final Loader loader;
	private final SgyFile selectedFile;
	private final PythonScriptExecutorService scriptExecutorService;
	private final Status status;
	private final Stage primaryStage = AppContext.stage;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final ProgressIndicator progressIndicator;
	private final VBox parametersBox;
	private final ComboBox<String> scriptsMetadataSelector;
	private final Button applyButton;

	public ScriptExecutionView(Model model, Loader loader, Status status, SgyFile selectedFile, PythonScriptExecutorService scriptExecutorService) {
		this.loader = loader;
		this.status = status;
		this.selectedFile = selectedFile;
		this.scriptExecutorService = scriptExecutorService;

		setSpacing(OptionPane.DEFAULT_SPACING);
		setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

		progressIndicator = new ProgressIndicator();
		progressIndicator.setVisible(false);

		scriptsMetadataSelector = new ComboBox<>();
		scriptsMetadataSelector.setPromptText("Select Python script");
		scriptsMetadataSelector.setMaxWidth(Double.MAX_VALUE);

		List<ScriptMetadata> loadedScriptsMetadata;
		try {
			ScriptMetadataLoader scriptsMetadataLoader = new JsonScriptMetadataLoader();
			Path scriptsPath;
			try {
				scriptsPath = scriptExecutorService.getScriptsPath();
			} catch (URISyntaxException e) {
				log.error("Failed to get scripts path", e);
				showExceptionDialog("No scripts directory. Please check that scripts directory exists and is accessible.");
				scriptsPath = Path.of("");
			}
			log.info("Loading Python scripts metadata from: {}", scriptsPath);
			loadedScriptsMetadata = scriptsMetadataLoader.loadScriptMetadata(scriptsPath);
		} catch (IOException e) {
			log.warn("Failed to load Python scripts", e);
			loadedScriptsMetadata = List.of();
		}
		String currentFileTemplate = FileTemplate.getTemplateName(model, selectedFile.getFile());
		final List<ScriptMetadata> scriptsMetadata = loadedScriptsMetadata.stream()
				.filter(metadata -> metadata.templates().isEmpty() || metadata.templates().contains(currentFileTemplate))
				.toList();

		scriptsMetadataSelector.getItems().addAll(
				scriptsMetadata.stream()
						.map(ScriptMetadata::filename)
						.toList()
		);

		parametersBox = new VBox(OptionPane.DEFAULT_SPACING);
		parametersBox.setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

		Label parametersLabel = new Label("Parameters:");
		parametersLabel.setStyle("-fx-font-weight: bold;");
		parametersLabel.setVisible(false);

		applyButton = new Button("Apply");
		applyButton.setDisable(true);

		scriptsMetadataSelector.setOnAction(e -> {
			String filename = scriptsMetadataSelector.getValue();
			ScriptMetadata selected = scriptsMetadata.stream()
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
			ScriptMetadata selected = scriptsMetadata.stream()
					.filter(scriptMetadata -> scriptMetadata.filename.equals(filename))
					.findFirst()
					.orElse(null);
			executeScript(selected);
		});

		VBox contentBox = new VBox();
		contentBox.getChildren().addAll(scriptsMetadataSelector, parametersBox, applyButton);

		StackPane stackPane = new StackPane(contentBox, progressIndicator);
		StackPane.setAlignment(progressIndicator, Pos.CENTER);

		getChildren().add(stackPane);

		if (scriptExecutorService.isExecuting(selectedFile)) {
			setExecutingProgress(true);
			String executingScriptName = scriptExecutorService.getExecutingScriptName(selectedFile);
			if (executingScriptName != null) {
				scriptsMetadataSelector.getSelectionModel().select(executingScriptName);
			} else {
				scriptsMetadataSelector.getSelectionModel().clearSelection();
			}
			setVisible(true);
			setManaged(true);
		} else {
			setVisible(false);
			setManaged(false);
		}
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

	private void executeScript(@Nullable ScriptMetadata scriptMetadata) {
		if (scriptMetadata == null)
			return;
		setExecutingProgress(true);

		Map<String, String> parameters = extractScriptParams(scriptMetadata.parameters);
		Future<PythonScriptExecutorService.ScriptExecutionResult> future =
				scriptExecutorService.executeScriptAsync(selectedFile, scriptMetadata, parameters);

		executor.submit(() -> {
			try {
				PythonScriptExecutorService.ScriptExecutionResult result = future.get();
				handleScriptResult(result, scriptMetadata);
			} catch (Exception e) {
				showExceptionDialog(e.getMessage());
			} finally {
				setExecutingProgress(false);
			}
		});
	}

	private void handleScriptResult(PythonScriptExecutorService.ScriptExecutionResult result, ScriptMetadata scriptMetadata) {
		if (result instanceof PythonScriptExecutorService.ScriptExecutionResult.Success) {
			handleSuccessResult(result, scriptMetadata);
		} else {
			PythonScriptExecutorService.ScriptExecutionResult.Error errorResult = (PythonScriptExecutorService.ScriptExecutionResult.Error) result;
			showErrorDialog(scriptMetadata.displayName, errorResult.getCode(), errorResult.getOutput());
		}
	}

	private void handleSuccessResult(PythonScriptExecutorService.ScriptExecutionResult result, ScriptMetadata scriptMetadata) {
		String successMessage = "Python script '" + scriptMetadata.displayName + "' executed successfully.";
		String output = result.getOutput();
		if (output != null && !output.isEmpty()) {
			successMessage += "\nOutput: " + output;
		}
		status.showMessage(successMessage, "Python Script");
		File currentFile = selectedFile.getFile();
		if (currentFile != null && currentFile.exists()) {
            Platform.runLater(() -> loader.load(List.of(currentFile)));
		} else {
			showExceptionDialog("Selected file does not exist or is not valid.");
		}
	}

	private Map<String, String> extractScriptParams(List<PythonScriptParameter> params) {
		Map<String, String> parameters = new HashMap<>();
		for (Node paramBox : parametersBox.getChildren()) {
			if (!(paramBox instanceof VBox)) continue;

			for (Node inputNode : ((VBox) paramBox).getChildren()) {
				String paramName = (String) inputNode.getUserData();
				if (paramName == null) continue;

				String value = extractValueFromNode(inputNode);
				boolean isRequired = params.stream()
						.anyMatch(param -> param.name().equals(paramName) && param.required());

				if (!value.isEmpty() || isRequired) {
					parameters.put(paramName, value);
				}
			}
		}
		return parameters;
	}

	private String extractValueFromNode(Node node) {
		return switch (node) {
			case TextField textField -> textField.getText();
			case CheckBox checkBox -> String.valueOf(checkBox.isSelected());
			default -> "";
		};
	}

	private void setExecutingProgress(boolean inProgress) {
		progressIndicator.setVisible(inProgress);
		progressIndicator.setManaged(inProgress);
		parametersBox.setDisable(inProgress);
		applyButton.setDisable(inProgress);
		scriptsMetadataSelector.setDisable(inProgress);
	}

	private void showErrorDialog(String scriptName, int exitCode, @javax.annotation.Nullable String output) {
		Platform.runLater(() -> {
			Dialog<String> dialog = new Dialog<>();
			dialog.initOwner(primaryStage);
			dialog.setTitle("Script Execution Error");
			dialog.setHeaderText("Error");
			String errorMessage = "Python script '" + scriptName + "' failed with exit code " + exitCode + ".";
			if (output != null && !output.isEmpty()) {
				errorMessage += "\nOutput: " + output;
			}
			dialog.setContentText(errorMessage);
			dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
			dialog.showAndWait();
		});

	}

	private void showExceptionDialog(String errorMessage) {
		Platform.runLater(() -> {
			Dialog<String> dialog = new Dialog<>();
			dialog.initOwner(primaryStage);
			dialog.setTitle("Script Execution Error");
			dialog.setHeaderText("Error");
			dialog.setContentText("Failed to execute script: " + errorMessage);
			dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
			dialog.showAndWait();
		});
	}

	public record ScriptMetadata(
			String filename,
			@JsonProperty("display_name")
			String displayName,
			List<PythonScriptParameter> parameters,
			List<String> templates
	) {
	}

	public record PythonScriptParameter(
			String name,
			@JsonProperty("display_name")
			String displayName,
			ParameterType type,
			@JsonProperty("default_value")
			String defaultValue,
			boolean required
	) {
		public enum ParameterType {
			STRING, INTEGER, DOUBLE, BOOLEAN, FILE_PATH
		}
	}
}
