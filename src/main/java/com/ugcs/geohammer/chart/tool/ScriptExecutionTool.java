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
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.SeriesSelectedEvent;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.service.script.CommandExecutionException;
import com.ugcs.geohammer.service.script.JsonScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptExecutor;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.service.script.ScriptValidationException;
import com.ugcs.geohammer.util.FileNames;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@Component
public class ScriptExecutionTool extends FilterToolView {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutionTool.class);

	private static final int MAX_OUTPUT_LINES_IN_DIALOG = 7;

	private static final String PREFS_NODE_NAME = "script_execution_tool";
	public static final String PREFS_LAST_SELECTED_SCRIPT_PREFIX = "last_selected_script";

	private final Model model;

	private final ScriptExecutor scriptExecutor;

	private final Status status;

	private final ExecutorService executor;

	private final PrefSettings preferences;

	private final VBox parametersBox;

	private final ComboBox<ScriptMetadata> scriptsMetadataSelector;

	private List<ScriptMetadata> scriptsMetadata = List.of();

	private final AtomicBoolean isUpdatingColumns = new AtomicBoolean(false);


	public ScriptExecutionTool(Model model, ExecutorService executor, Status status, PrefSettings preferences, ScriptExecutor scriptExecutor) {
		super(executor);

        this.model = model;
        this.executor = executor;
		this.status = status;
		this.preferences = preferences;
		this.scriptExecutor = scriptExecutor;

		scriptsMetadataSelector = new ComboBox<>();
		scriptsMetadataSelector.setPromptText("Select script");
		scriptsMetadataSelector.setMaxWidth(Double.MAX_VALUE);

		parametersBox = new VBox(Tools.DEFAULT_SPACING);
		parametersBox.setPadding(Tools.DEFAULT_OPTIONS_INSETS);

		scriptsMetadataSelector.setCellFactory(param -> createScriptMetadataCell());
		scriptsMetadataSelector.setButtonCell(createScriptMetadataCell());
		scriptsMetadataSelector.setOnAction(e -> {
			ScriptMetadata scriptMetadata = scriptsMetadataSelector.getValue();
			updateParametersBox(scriptMetadata);
			String templateName = Templates.getTemplateName(selectedFile);
			if (scriptMetadata != null && templateName != null) {
				preferences.setValue(
						PREFS_NODE_NAME,
						PREFS_LAST_SELECTED_SCRIPT_PREFIX + "_" + templateName,
						scriptMetadata.filename());
			}
		});

        inputContainer.getChildren().setAll(scriptsMetadataSelector, parametersBox);

        showApply(false);
        showApplyToAll(false);

		updateView();
	}

	private ListCell<ScriptMetadata> createScriptMetadataCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(ScriptMetadata item, boolean empty) {
				super.updateItem(item, empty);
				if (item == null || empty) {
					setText(null);
				} else {
					setText(item.displayName());
				}
			}
		};
	}

	private void updateParametersBox(ScriptMetadata scriptMetadata) {
		parametersBox.getChildren().clear();

		if (scriptMetadata != null) {
			for (ScriptParameter param : scriptMetadata.parameters()) {
				String initialValue = loadStoredParamValue(scriptMetadata.filename(), param.name(),
						param.defaultValue());
				VBox paramBox = createParameterInput(param, initialValue);
				parametersBox.getChildren().add(paramBox);
			}

			showApply(true);
			showApplyToAll(true);
		} else {
			showApply(false);
			showApplyToAll(false);
		}
	}

	@Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile || file instanceof TraceFile;
    }

    @Override
    protected void onApply(ActionEvent event) {
		ScriptMetadata scriptMetadata = scriptsMetadataSelector.getValue();
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
		ScriptMetadata scriptMetadata = scriptsMetadataSelector.getValue();
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
		String templateName = Templates.getTemplateName(selectedFile);
		String selectedScriptFilename = preferences.getString(PREFS_NODE_NAME, PREFS_LAST_SELECTED_SCRIPT_PREFIX + "_" + templateName);
		@Nullable ScriptMetadata selectedScriptMetadata = scriptsMetadata.stream()
				.filter(scriptMetadata -> scriptMetadata.filename().equals(selectedScriptFilename))
				.findAny()
				.orElse(null);
		scriptsMetadataSelector.getItems().setAll(
				scriptsMetadata
		);
		if (selectedScriptMetadata != null && scriptsMetadataSelector.getItems().contains(selectedScriptMetadata)) {
			scriptsMetadataSelector.getSelectionModel().select(selectedScriptMetadata);
		} else {
			scriptsMetadataSelector.getSelectionModel().clearSelection();
			scriptsMetadataSelector.setValue(null);
			scriptsMetadataSelector.setPromptText("Select script");
		}
	}

	private void refreshExecutionStatus(@Nullable SgyFile file) {
		if (scriptExecutor.isExecuting(file)) {
            disableAndShowProgress();
			ScriptMetadata executingScriptMetadata = scriptExecutor.getExecutingScriptMetadata(file);
			if (executingScriptMetadata != null) {
				scriptsMetadataSelector.getSelectionModel().select(executingScriptMetadata);
			} else {
				scriptsMetadataSelector.getSelectionModel().clearSelection();
				scriptsMetadataSelector.setValue(null);
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
		return switch (param.type()) {
			case STRING, FILE_PATH -> createTextField(param, initialValue);
			case INTEGER -> createIntegerField(param, initialValue);
			case DOUBLE -> createDoubleField(param, initialValue);
			case BOOLEAN -> createCheckBox(param, initialValue, labelText);
			case COLUMN_NAME -> createColumnSelector(param, initialValue);
		};
	}

	private static @NotNull TextField createTextField(ScriptParameter param, String initialValue) {
		TextField textField = new TextField(initialValue);
		textField.setUserData(param);
		textField.setPromptText(param.displayName());
		return textField;
	}

	private static @NotNull TextField createIntegerField(ScriptParameter param, String initialValue) {
		TextField textField = new TextField(initialValue);
		textField.setUserData(param);
		textField.setPromptText("Enter integer value");
		return textField;
	}

	private static @NotNull TextField createDoubleField(ScriptParameter param, String initialValue) {
		TextField textField = new TextField(initialValue);
		textField.setUserData(param);
		textField.setPromptText("Enter decimal value");
		return textField;
	}

	private static @NotNull CheckBox createCheckBox(ScriptParameter param, String initialValue, String labelText) {
		CheckBox checkBox = new CheckBox();
		checkBox.setUserData(param);
		checkBox.setSelected(Boolean.parseBoolean(initialValue));
		checkBox.setText(labelText);
		return checkBox;
	}

	private ComboBox<String> createColumnSelector(ScriptParameter param, String initialValue) {
		ComboBox<String> columnSelector = new ComboBox<>();
		columnSelector.setPromptText("Select column");
		columnSelector.setMaxWidth(Double.MAX_VALUE);

		columnSelector.setUserData(param);

		if (selectedFile instanceof CsvFile csvFile) {
			Set<String> columns = getAvailableColumnsForFile(csvFile);
			updateComboBoxIfChanged(columnSelector, columns, initialValue);
		}

		return columnSelector;
	}

	private Set<String> getAvailableColumnsForFile(CsvFile csvFile) {
		Chart chart = model.getChart(csvFile);
		if (chart instanceof SensorLineChart sensorChart) {
			return new TreeSet<>(sensorChart.getSeriesNames());
		}
		return Set.of();
	}

	private String loadStoredParamValue(@Nullable String scriptFilename, String paramName, String defaultValue) {
		if (scriptFilename == null) {
			return defaultValue;
		}
		String filename = FileNames.removeExtension(scriptFilename);
		return preferences.getStringOrDefault(PREFS_NODE_NAME, filename + "." + paramName, defaultValue);
	}

	private void storeParamValues(String scriptFilename, Map<String, String> params) {
		String filename = FileNames.removeExtension(scriptFilename);
		params.forEach((name, value) ->
				preferences.setValue(PREFS_NODE_NAME, filename + "." + name, value));
	}

	private void executeScript(@Nullable ScriptMetadata scriptMetadata, List<SgyFile> files) {
		if (scriptMetadata == null || files.isEmpty()) {
			log.error("No script selected for execution");
			return;
		}

		Map<String, String> parameters = extractScriptParams();
		try {
			scriptMetadata.validateRequiredParameters(parameters);
		} catch (ScriptValidationException e) {
			showError(e.getMessage());
			return;
		}
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
		String message;

        if (e instanceof CommandExecutionException commandExecutionException) {
            message = "Script '" + scriptMetadata.filename()
                    + "' failed with exit code " + commandExecutionException.getExitCode() + ".";
            if (!Strings.isNullOrEmpty(scriptOutput)) {
                message += System.lineSeparator() + "Output:"
                        + System.lineSeparator() + scriptOutput;
            }
        } else {
            message = "Failed to execute script: " + e.getMessage();
        }
		showError(message);
	}

	private void showError(String message) {
		MessageBoxHelper.showError("Script Execution Error", message);
	}

	private Map<String, String> extractScriptParams() {
		Map<String, String> parameters = new HashMap<>();
		for (Node paramBox : parametersBox.getChildren()) {
			if (!(paramBox instanceof VBox)) {
				continue;
			}

			for (Node inputNode : ((VBox) paramBox).getChildren()) {
				String paramName = switch (inputNode.getUserData()) {
					case ScriptParameter param -> param.name();
					case null, default -> null;
				};
				if (paramName == null) {
					continue;
				}

				String value = extractValueFromNode(inputNode);
				if (value == null) {
					continue;
				}
				parameters.put(paramName, value);
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
		if (isUpdatingColumns.get()) {
			return;
		}

		Platform.runLater(() -> refreshColumnSelectors(csvFile));
	}

	private void refreshColumnSelectors(CsvFile csvFile) {
		Set<String> availableColumns = getAvailableColumnsForFile(csvFile);

		//noinspection unchecked
		parametersBox.getChildren().stream()
				.filter(VBox.class::isInstance)
				.map(VBox.class::cast)
				.flatMap(vbox -> vbox.getChildren().stream())
				.filter(ComboBox.class::isInstance)
				.map(node -> (ComboBox<String>) node)
				.forEach(comboBox ->
						updateComboBoxIfChanged(comboBox, availableColumns, null)
				);
	}

	private void updateComboBoxIfChanged(ComboBox<String> comboBox, Set<String> availableColumns, @Nullable String initialValue) {
		Set<String> currentItems = new HashSet<>(comboBox.getItems());

		if (availableColumns.isEmpty()) {
			comboBox.setPromptText("No columns available");
			comboBox.setDisable(true);
			return;
		} else {
			comboBox.setDisable(false);
		}

		if (!currentItems.equals(availableColumns)) {
			isUpdatingColumns.set(true);
			String value = comboBox.getValue();

			comboBox.getItems().setAll(availableColumns);

			if (value != null && availableColumns.contains(value)) {
				comboBox.setValue(value);
			} else if (comboBox.getUserData() instanceof ScriptParameter param) {
				String defaultValue = param.defaultValue();
				if (initialValue != null && availableColumns.contains(initialValue)) {
					comboBox.setValue(initialValue);
				} else if (!defaultValue.isEmpty() && availableColumns.contains(defaultValue)) {
					comboBox.setValue(defaultValue);
				} else {
					comboBox.setValue(null);
				}
			} else {
				comboBox.setValue(null);
			}
			isUpdatingColumns.set(false);
		}
	}
}
