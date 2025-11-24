package com.ugcs.geohammer.chart;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.service.script.JsonScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptException;
import com.ugcs.geohammer.service.script.ScriptExecutor;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.view.MessageBoxHelper;
import com.ugcs.geohammer.view.status.Status;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.util.Strings;
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
import javafx.stage.FileChooser;
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

	public ScriptExecutionView(Model model, Status status, @Nullable SgyFile selectedFile, ScriptExecutor scriptExecutor) {
		this.model = model;
		this.status = status;
		this.scriptExecutor = scriptExecutor;
		this.selectedFile = selectedFile;

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
			ScriptMetadata selectedScript = scriptsMetadata.stream()
					.filter(scriptMetadata -> scriptMetadata.filename().equals(filename))
					.findFirst()
					.orElse(null);
			parametersBox.getChildren().clear();

			if (selectedScript != null) {
				if (selectedScript.parameters().isEmpty()) {
					parametersLabel.setVisible(false);
				} else {
					parametersLabel.setVisible(true);
					parametersBox.getChildren().add(parametersLabel);
				}

				for (ScriptParameter param : selectedScript.parameters()) {
					String initialValue = loadStoredParamValue(selectedScript.filename(), param.name(), param.defaultValue());
					VBox paramBox = createParameterInput(param, initialValue);
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

		applyButton.setOnAction(event -> onApplyClicked());
		applyToAllButton.setOnAction(event -> onApplyToAllClicked(model));

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

	private void onApplyClicked() {
		String filename = scriptsMetadataSelector.getValue();
		ScriptMetadata scriptMetadata = scriptsMetadata.stream()
				.filter(sM -> sM.filename().equals(filename))
				.findFirst()
				.orElse(null);
		SgyFile sgyFile = this.selectedFile;
		if (sgyFile != null) {
			executeScript(scriptMetadata, List.of(sgyFile));
		} else {
			MessageBoxHelper.showError("No file selected",
					"Please select a file to apply the script");
		}
	}

	private void onApplyToAllClicked(Model model) {
		String filename = scriptsMetadataSelector.getValue();
		ScriptMetadata scriptMetadata = scriptsMetadata.stream()
				.filter(sM -> sM.filename().equals(filename))
				.findFirst()
				.orElse(null);
		List<SgyFile> filesToProcess = getFilesToProcess(model);
		executeScript(scriptMetadata, filesToProcess);
	}

	private List<SgyFile> getFilesToProcess(Model model) {
		SgyFile sgyFile = this.selectedFile;
		String template = Templates.getTemplateName(sgyFile);
		List<SgyFile> filesToProcess = model.getFileManager().getFiles();
		if (template != null) {
			filesToProcess = filesToProcess.stream().filter(file -> {
				String fileTemplate = Templates.getTemplateName(file);
				return template.equals(fileTemplate);
			}).toList();
		}
		return filesToProcess;
	}

	public void updateView(@Nullable SgyFile sgyFile) {
		this.selectedFile = sgyFile;
		try {
			List<ScriptMetadata> loadedScriptsMetadata = getLoadedScriptsMetadata();
			this.scriptsMetadata = filterScriptsByTemplate(sgyFile, loadedScriptsMetadata);
		} catch (Exception e) {
			this.scriptsMetadata = List.of();
			MessageBoxHelper.showError("Scripts Directory Error",
					"Failed to load scripts metadata: " + e.getMessage());
		}

		restoreScriptSelection();

		refreshExecutionStatus(sgyFile);
	}

	private List<ScriptMetadata> getLoadedScriptsMetadata() throws IOException {
		ScriptMetadataLoader scriptsMetadataLoader = new JsonScriptMetadataLoader();
		Path scriptsPath = scriptExecutor.getScriptsPath();
		return scriptsMetadataLoader.loadScriptMetadata(scriptsPath);
	}

	private List<ScriptMetadata> filterScriptsByTemplate(@Nullable SgyFile sgyFile, List<ScriptMetadata> scriptsMetadata) {
		String fileTemplate = Templates.getTemplateName(sgyFile);
		if (fileTemplate == null) {
			return scriptsMetadata;
		} else {
			return scriptsMetadata.stream()
					.filter(metadata -> metadata.templates().contains(fileTemplate))
					.toList();
		}
	}

	private void restoreScriptSelection() {
		@Nullable String prevSelectedScript = scriptsMetadataSelector.getSelectionModel().getSelectedItem();
		scriptsMetadataSelector.getItems().setAll(
				scriptsMetadata.stream()
						.map(ScriptMetadata::filename)
						.toList()
		);
		if (prevSelectedScript != null && scriptsMetadataSelector.getItems().contains(prevSelectedScript)) {
			scriptsMetadataSelector.getSelectionModel().select(prevSelectedScript);
		} else {
			scriptsMetadataSelector.getSelectionModel().clearSelection();
			scriptsMetadataSelector.setPromptText("Select script");
		}
	}

	private void refreshExecutionStatus(@Nullable SgyFile sgyFile) {
		if (scriptExecutor.isExecuting(sgyFile)) {
			setExecutingProgress(true);
			String executingScriptName = scriptExecutor.getExecutingScriptName(sgyFile);
			if (executingScriptName != null) {
				scriptsMetadataSelector.getSelectionModel().select(executingScriptName);
			} else {
				scriptsMetadataSelector.getSelectionModel().clearSelection();
			}
		} else {
			setExecutingProgress(false);
		}
	}

	private VBox createParameterInput(ScriptParameter param, String initialValue) {
		VBox paramBox = new VBox(5);
		String labelText = param.displayName() + (param.required() ? " *" : "");
		Label label = new Label(labelText);

		Node inputNode = getInputNode(param, initialValue, labelText);
		if (param.type() != ScriptParameter.ParameterType.BOOLEAN) {
			paramBox.getChildren().add(label);
		}
		paramBox.getChildren().add(inputNode);
		return paramBox;
	}

	private static Node getInputNode(ScriptParameter param, String initialValue, String labelText) {
		Node inputNode = switch (param.type()) {
			case STRING -> {
				TextField textField = new TextField(initialValue);
				textField.setPromptText(param.displayName());
				yield textField;
			}
			case FILE_PATH -> createFileParameterInput(initialValue);
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

	private static HBox createFileParameterInput(String initialValue) {
		HBox fileBox = new HBox(5);
		TextField textField = new TextField(initialValue);
		textField.setPromptText("Select file path");
		textField.setEditable(false);
		HBox.setHgrow(textField, Priority.ALWAYS);

		Button browseButton = new Button("Browse...");
		browseButton.setOnAction(e -> {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Select File");
			String currentText = textField.getText();
			if (currentText != null && !currentText.isEmpty()) {
				File currentFile = new File(currentText);
				if (currentFile.getParentFile() != null && currentFile.getParentFile().exists()) {
					fileChooser.setInitialDirectory(currentFile.getParentFile());
				}
			}
			File selectedFile = fileChooser.showOpenDialog(browseButton.getScene().getWindow());
			if (selectedFile != null) {
				textField.setText(selectedFile.getAbsolutePath());
			}
		});

		fileBox.getChildren().addAll(textField, browseButton);
		return fileBox;
	}

	private String loadStoredParamValue(@Nullable String scriptFilename, String paramName, String defaultValue) {
		if (scriptFilename == null) {
			return defaultValue;
		}
		return prefs.get(scriptFilename + "." + paramName, defaultValue);
	}

	private void storeParamValues(String scriptFilename, Map<String, String> params) {
		params.forEach((name, value) -> prefs.put(scriptFilename + "." + name, value));
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

		Platform.runLater(() -> {
			setExecutingProgress(true);
			applyToAllButton.setDisable(true);
		});

		AtomicInteger remainingFiles = new AtomicInteger(files.size());
		Future<Void> future = executor.submit(() -> {
			for (SgyFile sgyFile : files) {
				try {
					scriptExecutor.executeScript(sgyFile, scriptMetadata, parameters, onScriptOutput);
					showSuccess(scriptMetadata);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					String scriptOutput = String.join(System.lineSeparator(), lastOutputLines);
					showError(scriptMetadata, e, scriptOutput);
				} finally {
					if (remainingFiles.decrementAndGet() == 0) {
						Platform.runLater(() -> {
							setExecutingProgress(false);
							applyToAllButton.setDisable(false);
						});
					}
				}
			}
			return null;
		});

		int filesCount = files.size();
		String taskName = "Running script " + scriptMetadata.displayName();
		if (filesCount == 1) {
			SgyFile sgyFile = files.getFirst();
			if (sgyFile.getFile() != null) {
				taskName += ": " + sgyFile.getFile().getName();
			}
		} else if (filesCount > 1) {
			taskName += " on " + filesCount + " files";
		}
		AppContext.getInstance(TaskService.class).registerTask(future, taskName);
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
			case HBox hBox -> {
				TextField textField = (TextField) hBox.getChildren().stream()
						.filter(n -> n instanceof TextField)
						.findFirst()
						.orElse(null);
				if (textField != null) {
					yield textField.getText();
				} else {
					yield "";
				}
			}
			default -> "";
		};
	}

	private void setExecutingProgress(boolean inProgress) {
		progressIndicator.setVisible(inProgress);
		progressIndicator.setManaged(inProgress);
		parametersBox.setDisable(inProgress);
		scriptsMetadataSelector.setDisable(inProgress);
		applyButton.setDisable(inProgress);
	}
}
