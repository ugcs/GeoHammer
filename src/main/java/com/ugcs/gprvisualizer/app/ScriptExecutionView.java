package com.ugcs.gprvisualizer.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.app.scripts.JsonScriptMetadataLoader;
import com.ugcs.gprvisualizer.app.scripts.ScriptException;
import com.ugcs.gprvisualizer.app.scripts.ScriptParameter;
import com.ugcs.gprvisualizer.app.scripts.ScriptMetadata;
import com.ugcs.gprvisualizer.app.scripts.ScriptMetadataLoader;
import com.ugcs.gprvisualizer.app.scripts.ScriptExecutor;
import com.ugcs.gprvisualizer.app.service.task.TaskService;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.FileTemplate;
import com.ugcs.gprvisualizer.utils.Strings;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class ScriptExecutionView extends VBox {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutionView.class);

	private static final int MAX_OUTPUT_LINES_IN_DIALOG = 7;

	private final Model model;

	@Nullable
	private SgyFile selectedFile;

	private final ScriptExecutor scriptExecutor;

	private final Status status;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final ProgressIndicator progressIndicator;

	private final VBox parametersBox;

	private final ComboBox<String> scriptsMetadataSelector;

	private final Button applyButton;

	private final Button applyToAllButton;

	private List<ScriptMetadata> scriptsMetadata = List.of();

	private final Preferences prefs = Preferences.userNodeForPackage(ScriptExecutionView.class);

	@Nullable
	private ScriptMetadata currentScriptMetadata = null;

	public ScriptExecutionView(Model model, Status status, @Nullable SgyFile selectedFile, ScriptExecutor scriptExecutor) {
		this.model = model;
		this.status = status;
		this.scriptExecutor = scriptExecutor;

		setSpacing(OptionPane.DEFAULT_SPACING);
		setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

		progressIndicator = new ProgressIndicator();
		progressIndicator.setVisible(false);

		scriptsMetadataSelector = new ComboBox<>();
		scriptsMetadataSelector.setPromptText("Select script");
		scriptsMetadataSelector.setMaxWidth(Double.MAX_VALUE);

		parametersBox = new VBox(OptionPane.DEFAULT_SPACING);
		parametersBox.setPadding(OptionPane.DEFAULT_OPTIONS_INSETS);

		Label parametersLabel = new Label("Parameters:");
		parametersLabel.setStyle("-fx-font-weight: bold;");
		parametersLabel.setVisible(false);

		applyButton = new Button("Apply");
		applyButton.setVisible(false);

		applyToAllButton = new Button("Apply to all");
		applyToAllButton.setVisible(false);

		scriptsMetadataSelector.setOnAction(e -> {
			String filename = scriptsMetadataSelector.getValue();
			ScriptMetadata selected = scriptsMetadata.stream()
					.filter(scriptMetadata -> scriptMetadata.filename().equals(filename))
					.findFirst()
					.orElse(null);

			currentScriptMetadata = selected;
			parametersBox.getChildren().clear();

			if (selected != null) {
				if (selected.parameters().isEmpty()) {
					parametersLabel.setVisible(false);
				} else {
					parametersLabel.setVisible(true);
					parametersBox.getChildren().add(parametersLabel);
				}

				for (ScriptParameter param : selected.parameters()) {
					VBox paramBox = createParameterInput(param);
					parametersBox.getChildren().add(paramBox);
				}

				applyButton.setVisible(true);
				applyToAllButton.setVisible(true);
			} else {
				parametersLabel.setVisible(false);
				applyButton.setVisible(false);
				applyToAllButton.setVisible(false);
			}
		});

		applyButton.setOnAction(event -> {
			String filename = scriptsMetadataSelector.getValue();
			ScriptMetadata scriptMetadata = scriptsMetadata.stream()
					.filter(sM -> sM.filename().equals(filename))
					.findFirst()
					.orElse(null);
			if (selectedFile != null) {
				executeScript(scriptMetadata, List.of(selectedFile));
			}
		});

		applyToAllButton.setOnAction(event -> {
			String filename = scriptsMetadataSelector.getValue();
			ScriptMetadata scriptMetadata = scriptsMetadata.stream()
					.filter(sM -> sM.filename().equals(filename))
					.findFirst()
					.orElse(null);
			List<SgyFile> filesToProcess = model.getFileManager().getFiles().stream().toList();
			executeScript(scriptMetadata, filesToProcess);
		});

		HBox buttonsRow = new HBox(5);
		HBox rightBox = new HBox();
		HBox leftBox = new HBox(5);
		leftBox.getChildren().addAll(applyButton);
		HBox.setHgrow(leftBox, Priority.ALWAYS);
		rightBox.getChildren().addAll(applyToAllButton);

		buttonsRow.getChildren().addAll(leftBox, rightBox);

		VBox contentBox = new VBox();
		contentBox.getChildren().addAll(scriptsMetadataSelector, parametersBox, buttonsRow);

		StackPane stackPane = new StackPane(contentBox, progressIndicator);
		StackPane.setAlignment(progressIndicator, Pos.CENTER);

		getChildren().add(stackPane);

		updateView(selectedFile);

		setVisible(false);
		setManaged(false);
	}

	public void updateView(@Nullable SgyFile newSelectedFile) {
		this.selectedFile = newSelectedFile;
		Platform.runLater(() -> {
			List<ScriptMetadata> loadedScriptsMetadata;
			try {
				ScriptMetadataLoader scriptsMetadataLoader = new JsonScriptMetadataLoader();
				Path scriptsPath = scriptExecutor.getScriptsPath();
				loadedScriptsMetadata = scriptsMetadataLoader.loadScriptMetadata(scriptsPath);
			} catch (IOException e) {
				log.warn("Failed to load scripts", e);
				MessageBoxHelper.showError("Scripts Directory Error",
						"Failed to load scripts metadata: " + e.getMessage());
				loadedScriptsMetadata = List.of();
			}
			String currentFileTemplate = selectedFile != null ? FileTemplate.getTemplateName(model, selectedFile.getFile()) : null;
			this.scriptsMetadata = loadedScriptsMetadata.stream()
					.filter(metadata -> metadata.templates().isEmpty() || metadata.templates().contains(currentFileTemplate))
					.toList();

			@Nullable String prevSelected = scriptsMetadataSelector.getSelectionModel().getSelectedItem();
			scriptsMetadataSelector.getItems().setAll(
					scriptsMetadata.stream()
							.map(ScriptMetadata::filename)
							.toList()
			);
			if (prevSelected != null && scriptsMetadataSelector.getItems().contains(prevSelected)) {
				scriptsMetadataSelector.getSelectionModel().select(prevSelected);
			} else {
				scriptsMetadataSelector.getSelectionModel().clearSelection();
				parametersBox.getChildren().clear();
			}

			if (selectedFile != null && scriptExecutor.isExecuting(selectedFile)) {
				setExecutingProgress(true);
				String executingScriptName = scriptExecutor.getExecutingScriptName(selectedFile);
				if (executingScriptName != null) {
					scriptsMetadataSelector.getSelectionModel().select(executingScriptName);
				} else {
					scriptsMetadataSelector.getSelectionModel().clearSelection();
				}
			} else {
				setExecutingProgress(false);
			}
		});
	}

	private VBox createParameterInput(ScriptParameter param) {
		VBox paramBox = new VBox(5);
		String labelText = param.displayName() + (param.required() ? " *" : "");
		Label label = new Label(labelText);

		String scriptFilename = currentScriptMetadata != null ? currentScriptMetadata.filename() : null;
		String initialValue = loadStoredParamValue(scriptFilename, param.name(), param.defaultValue());

		Node inputNode = getInputNode(param, initialValue, labelText);
		if (param.type() != ScriptParameter.ParameterType.BOOLEAN) {
			paramBox.getChildren().add(label);
		}
		paramBox.getChildren().add(inputNode);
		return paramBox;
	}

	private static Node getInputNode(ScriptParameter param, String initialValue, String labelText) {
		Node inputNode = switch (param.type()) {
			case STRING, FILE_PATH -> {
				TextField textField = new TextField(initialValue);
				textField.setPromptText(param.displayName());
				yield textField;
			}
			case INTEGER -> {
				TextField textField = new TextField(initialValue);
				textField.setPromptText("Enter integer value");
				yield textField;
			}
			case DOUBLE -> {
				TextField textField = new TextField(initialValue);
				textField.setPromptText("Enter decimal value");
				yield textField;
			}
			case BOOLEAN -> {
				CheckBox checkBox = new CheckBox();
				checkBox.setSelected(Boolean.parseBoolean(initialValue));
				checkBox.setText(labelText);
				yield checkBox;
			}
		};

		inputNode.setUserData(param.name());
		return inputNode;
	}

	private String loadStoredParamValue(@Nullable String scriptFilename, String paramName, String defaultValue) {
		if (scriptFilename == null) {
			return defaultValue;
		}
		return prefs.get("script." + scriptFilename + "." + paramName, defaultValue);
	}

	private void storeParamValues(String scriptFilename, Map<String, String> params) {
		params.forEach((k, v) -> prefs.put("script." + scriptFilename + "." + k, v));
	}

	private void executeScript(@Nullable ScriptMetadata scriptMetadata, List<SgyFile> files) {
		if (scriptMetadata == null || files.isEmpty()) {
			log.error("No script selected for execution");
			return;
		}

		Map<String, String> parameters = extractScriptParams(scriptMetadata.parameters());
		storeParamValues(scriptMetadata.filename(), parameters);

		ArrayDeque<String> lastOutputLines = new ArrayDeque<>();
		Consumer<String> onScriptOutput = line -> {
			if (Strings.isNullOrEmpty(line)) {
				return;
			}
			lastOutputLines.offer(line);
			if (lastOutputLines.size() > MAX_OUTPUT_LINES_IN_DIALOG) {
				lastOutputLines.poll();
			}
			status.showMessage(line, scriptMetadata.displayName());
		};

		setExecutingProgress(true);

		List<Future<Void>> futures = new ArrayList<>();
		for (SgyFile sgyFile : files) {
			Future<Void> future = executor.submit(() -> {
				try {
					scriptExecutor.executeScript(sgyFile, scriptMetadata, parameters, onScriptOutput);
					showSuccess(scriptMetadata);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					String scriptOutput = String.join(System.lineSeparator(), lastOutputLines);
					showError(scriptMetadata, e, scriptOutput);
				}
				return null;
			});

			String taskName = "Running script " + scriptMetadata.displayName();
			if (sgyFile.getFile() != null) {
				taskName += ": " + sgyFile.getFile().getName();
			}
			AppContext.getInstance(TaskService.class).registerTask(future, taskName);
			futures.add(future);
		}

		executor.submit(() -> {
			try {
				for (Future<Void> future : futures) {
					try {
						future.get();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					} catch (ExecutionException ee) {
						showError(scriptMetadata, ee, null);
					}
				}
			} finally {
				setExecutingProgress(false);
			}
			return null;
		});
	}

	private void showSuccess(ScriptMetadata scriptMetadata) {
		status.showMessage("Script executed successfully.", scriptMetadata.displayName());
	}

	private void showError(ScriptMetadata scriptMetadata, Exception e, @Nullable String scriptOutput) {
		String title = "Script Execution Error";
		String message;

        if (e instanceof ScriptException scriptException) {
            message = "Script '" + scriptMetadata.filename()
                    + "' failed with exit code " + scriptException.getExitCode() + ".";
            if (!Strings.isNullOrEmpty(scriptOutput)) {
                message += System.lineSeparator() + "Output:"
                        + System.lineSeparator() + scriptOutput;
            }
        } else {
            message = "Failed to execute script: " + e.getMessage();
        }
		MessageBoxHelper.showError(title, message);
	}

	private Map<String, String> extractScriptParams(List<ScriptParameter> params) {
		Map<String, String> parameters = new HashMap<>();
		for (Node paramBox : parametersBox.getChildren()) {
			if (!(paramBox instanceof VBox)) {
				continue;
			}

			for (Node inputNode : ((VBox) paramBox).getChildren()) {
				String paramName = (String) inputNode.getUserData();
				if (paramName == null) {
					continue;
				}

				String value = extractValueFromNode(inputNode);
				if (value == null) {
					continue;
				}
				boolean isRequired = params.stream()
						.anyMatch(param -> param.name().equals(paramName) && param.required());
				if (!value.isEmpty() || isRequired) {
					parameters.put(paramName, value);
				}
			}
		}
		return parameters;
	}

	@Nullable
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
		scriptsMetadataSelector.setDisable(inProgress);
		applyButton.setDisable(inProgress);
		applyToAllButton.setDisable(inProgress);
	}
}
