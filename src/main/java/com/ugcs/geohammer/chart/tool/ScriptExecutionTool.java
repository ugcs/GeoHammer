package com.ugcs.geohammer.chart.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesSelectedEvent;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.service.script.JsonScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptException;
import com.ugcs.geohammer.service.script.ScriptExecutor;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.view.MessageBoxHelper;
import com.ugcs.geohammer.view.status.Status;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@Component
public class ScriptExecutionTool extends FilterToolView {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutionTool.class);

	private static final int MAX_OUTPUT_LINES_IN_DIALOG = 7;

	private final Model model;

	private final ScriptExecutor scriptExecutor;

	private final Status status;

	private final ExecutorService executor;

	private final VBox parametersBox;

	private final ComboBox<String> scriptsMetadataSelector;

	private final Preferences prefs = Preferences.userNodeForPackage(ScriptExecutionTool.class);

	private List<ScriptMetadata> scriptsMetadata = List.of();

	private volatile boolean isUpdatingColumns = false;


	public ScriptExecutionTool(Model model, ExecutorService executor, Status status, ScriptExecutor scriptExecutor) {
		super(executor);

        this.model = model;
        this.executor = executor;
		this.status = status;
		this.scriptExecutor = scriptExecutor;

		scriptsMetadataSelector = new ComboBox<>();
		scriptsMetadataSelector.setPromptText("Select script");
		scriptsMetadataSelector.setMaxWidth(Double.MAX_VALUE);

		parametersBox = new VBox(Tools.DEFAULT_SPACING);
		parametersBox.setPadding(Tools.DEFAULT_OPTIONS_INSETS);

		Label parametersLabel = new Label("Parameters:");
		parametersLabel.setStyle("-fx-font-weight: bold;");
		parametersLabel.setVisible(false);

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

                showApply(true);
                showApplyToAll(true);
			} else {
				parametersLabel.setVisible(false);
                showApply(false);
                showApplyToAll(false);
			}
		});

        inputContainer.getChildren().setAll(scriptsMetadataSelector, parametersBox);

        showApply(false);
        showApplyToAll(false);

		updateView();
	}

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile || file instanceof TraceFile;
    }

    @Override
    protected void onApply(ActionEvent event) {
        String filename = scriptsMetadataSelector.getValue();
        ScriptMetadata scriptMetadata = scriptsMetadata.stream()
                .filter(sM -> sM.filename().equals(filename))
                .findFirst()
                .orElse(null);
        SgyFile sgyFile = selectedFile;
        if (sgyFile != null) {
            executeScript(scriptMetadata, List.of(sgyFile));
        } else {
            MessageBoxHelper.showError("No file selected",
                    "Please select a file to apply the script");
        }
    }

    @Override
    protected void onApplyToAll(ActionEvent event) {
        String filename = scriptsMetadataSelector.getValue();
        ScriptMetadata scriptMetadata = scriptsMetadata.stream()
                .filter(sM -> sM.filename().equals(filename))
                .findFirst()
                .orElse(null);
        List<SgyFile> filesToProcess = getFilesToProcess(model);
        executeScript(scriptMetadata, filesToProcess);
    }

	private List<SgyFile> getFilesToProcess(Model model) {
		SgyFile sgyFile = selectedFile;
		return model.getFileManager().getFiles().stream()
                .filter(file -> Templates.equals(file, sgyFile))
                .toList();
	}

    @Override
	public void updateView() {
        SgyFile file = selectedFile;
		try {
			List<ScriptMetadata> loadedScriptsMetadata = getLoadedScriptsMetadata();
			scriptsMetadata = filterScriptsByTemplate(file, loadedScriptsMetadata);
		} catch (Exception e) {
			scriptsMetadata = List.of();
			MessageBoxHelper.showError("Scripts Directory Error",
					"Failed to load scripts metadata: " + e.getMessage());
		}

		restoreScriptSelection();
		refreshExecutionStatus(file);
	}

	private List<ScriptMetadata> getLoadedScriptsMetadata() throws IOException {
		ScriptMetadataLoader scriptsMetadataLoader = new JsonScriptMetadataLoader();
		Path scriptsPath = scriptExecutor.getScriptsPath();
		return scriptsMetadataLoader.loadScriptMetadata(scriptsPath);
	}

	private List<ScriptMetadata> filterScriptsByTemplate(@Nullable SgyFile file, List<ScriptMetadata> scriptsMetadata) {
		String fileTemplate = Templates.getTemplateName(file);
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

	private void refreshExecutionStatus(@Nullable SgyFile file) {
		if (scriptExecutor.isExecuting(file)) {
            disableAndShowProgress();
			String executingScriptName = scriptExecutor.getExecutingScriptName(file);
			if (executingScriptName != null) {
				scriptsMetadataSelector.getSelectionModel().select(executingScriptName);
			} else {
				scriptsMetadataSelector.getSelectionModel().clearSelection();
			}
		} else {
            enableAndHideProgress();
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

	private Node getInputNode(ScriptParameter param, String initialValue, String labelText) {
		Node inputNode = switch (param.type()) {
			case STRING, FILE_PATH -> {
				TextField textField = new TextField(initialValue);
				textField.setUserData(param.name());
				textField.setPromptText(param.displayName());
				yield textField;
			}
			case INTEGER -> {
				TextField textField = new TextField(initialValue);
				textField.setUserData(param.name());
				textField.setPromptText("Enter integer value");
				yield textField;
			}
			case DOUBLE -> {
				TextField textField = new TextField(initialValue);
				textField.setUserData(param.name());
				textField.setPromptText("Enter decimal value");
				yield textField;
			}
			case BOOLEAN -> {
				CheckBox checkBox = new CheckBox();
				checkBox.setUserData(param.name());
				checkBox.setSelected(Boolean.parseBoolean(initialValue));
				checkBox.setText(labelText);
				yield checkBox;
			}
			case COLUMN_NAME -> createColumnSelector(param, initialValue);
		};

		return inputNode;
	}

	private ComboBox<String> createColumnSelector(ScriptParameter param, String initialValue) {
		ComboBox<String> comboBox = new ComboBox<>();
		comboBox.setUserData(param);
		comboBox.setPromptText("Select column");
		comboBox.setMaxWidth(Double.MAX_VALUE);

		if (selectedFile instanceof CsvFile csvFile) {
			Set<String> columns = getAvailableColumnsForFile(csvFile);
			comboBox.getItems().setAll(columns);

			if (columns.isEmpty()) {
				comboBox.setPromptText("No columns available");
				comboBox.setDisable(true);
			} else {
				if (initialValue != null) {
					comboBox.setValue(initialValue);
				}
			}
		}

		return comboBox;
	}

	private Set<String> getAvailableColumnsForFile(CsvFile csvFile) {
		Set<String> seriesNames = new HashSet<>();
		for (SgyFile file : model.getFileManager().getFiles()) {
			if (Objects.equals(file, csvFile)) {
				Chart chart = model.getChart(file);
				if (chart instanceof SensorLineChart sensorChart) {
					seriesNames.addAll(sensorChart.getSeriesNames());
				}
			}
		}

		return seriesNames;
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

		Platform.runLater(this::disableAndShowProgress);

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
						Platform.runLater(this::enableAndHideProgress);
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
				String paramName = switch (inputNode.getUserData()) {
					case ScriptParameter param -> param.name();
					case String name -> name;
					case null, default -> null;
				};
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
			case ComboBox<?> comboBox -> {
				Object value = comboBox.getValue();
				yield value != null ? value.toString() : "";
			}
			default -> "";
		};
	}

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

	@EventListener
	private void onSeriesSelected(SeriesSelectedEvent event) {
		SgyFile file = event.getFile();
		if (!Objects.equals(file, selectedFile)) {
			return;
		}
		if (!(selectedFile instanceof CsvFile csvFile)) {
			return;
		}
		if (isUpdatingColumns) {
			return;
		}

		Platform.runLater(() -> {
			try {
				isUpdatingColumns = true;
				updateColumnSelectorsIfNeeded(csvFile);
			} finally {
				isUpdatingColumns = false;
			}
		});
	}

	private void updateColumnSelectorsIfNeeded(CsvFile csvFile) {
		Set<String> availableColumns = getAvailableColumnsForFile(csvFile);

		//noinspection unchecked
		parametersBox.getChildren().stream()
				.filter(VBox.class::isInstance)
				.map(VBox.class::cast)
				.flatMap(vbox -> vbox.getChildren().stream())
				.filter(ComboBox.class::isInstance)
				.map(node -> (ComboBox<String>) node)
				.findFirst()
				.ifPresent(comboBox -> updateComboBoxIfChanged(comboBox, availableColumns));
	}

	private void updateComboBoxIfChanged(ComboBox<String> comboBox, Set<String> newItems) {
		Set<String> currentItems = new HashSet<>(comboBox. getItems());

		if (!currentItems.equals(newItems)) {
			String currentValue = comboBox.getValue();

			comboBox.getItems().setAll(newItems);

			if (currentValue != null && newItems.contains(currentValue)) {
				comboBox.setValue(currentValue);
			} else if (comboBox.getUserData() instanceof ScriptParameter param) {
				String defaultValue = param.defaultValue();
				if (!defaultValue.isEmpty() && newItems.contains(defaultValue)) {
					comboBox.setValue(defaultValue);
				} else {
					comboBox.setValue(null);
				}
			} else {
				comboBox.setValue(null);
			}
		}
	}
}
