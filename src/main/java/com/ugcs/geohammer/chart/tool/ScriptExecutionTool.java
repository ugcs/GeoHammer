package com.ugcs.geohammer.chart.tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
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
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.service.script.ScriptPaths;
import com.ugcs.geohammer.service.script.ScriptRunListener;
import com.ugcs.geohammer.service.script.ScriptRunner;
import com.ugcs.geohammer.service.script.ScriptValidationException;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.status.Status;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ScriptExecutionTool extends FilterToolView implements ScriptRunListener {

	private static final Logger log = LoggerFactory.getLogger(ScriptExecutionTool.class);

	private static final String PREFS_NODE_NAME = "script_execution_tool";
	public static final String PREFS_LAST_SELECTED_SCRIPT_PREFIX = "last_selected_script";

	private final Model model;

	private final ScriptRunner scriptRunner;

	private final ScriptPaths scriptPaths;

	private final ScriptMetadataLoader scriptMetadataLoader;

	private final TaskService taskService;

	private final Status status;

	private final ExecutorService executor;

	private final PrefSettings preferences;

	private final VBox parametersBox;

	private final ComboBox<ScriptMetadata> scriptsMetadataSelector;

	private List<ScriptMetadata> scriptsMetadata = List.of();

	private final AtomicBoolean isUpdatingColumns = new AtomicBoolean(false);


	public ScriptExecutionTool(Model model, ExecutorService executor, Status status, PrefSettings preferences,
			ScriptRunner scriptRunner, ScriptPaths scriptPaths,
			ScriptMetadataLoader scriptMetadataLoader, TaskService taskService) {
		super(executor);

        this.model = model;
        this.executor = executor;
		this.status = status;
		this.preferences = preferences;
		this.scriptRunner = scriptRunner;
		this.scriptPaths = scriptPaths;
		this.scriptMetadataLoader = scriptMetadataLoader;
		this.taskService = taskService;

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
			try {
				for (ScriptParameter param : scriptMetadata.parameters()) {
					param.validate();
					String initialValue = loadStoredParamValue(scriptMetadata.filename(), param.name(),
							param.defaultValue());
					parametersBox.getChildren().add(createParameterInput(param, initialValue));
				}
				showApply(true);
				showApplyToAll(true);
			} catch (IllegalArgumentException e) {
				Dialogs.showError("Invalid script metadata", e.getMessage());
				showApply(false);
				showApplyToAll(false);
			}
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
            Dialogs.showError("No file selected",
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
			Dialogs.showError("Failed to load scripts metadata", e);
		}

		restoreScriptSelection();
		refreshExecutionStatus(file);
	}

	private List<ScriptMetadata> getLoadedScriptsMetadata() throws IOException {
		Path scriptsPath = scriptPaths.getScriptsPath();
		return scriptMetadataLoader.loadScriptMetadata(scriptsPath);
	}

	private List<ScriptMetadata> filterScriptsByTemplate(@Nullable SgyFile file, List<ScriptMetadata> scriptsMetadata) {
		String fileTemplate = Templates.getTemplateName(file);
		if (fileTemplate == null) {
			return scriptsMetadata;
		}
		List<ScriptMetadata> result = new ArrayList<>();
		for (ScriptMetadata metadata : scriptsMetadata) {
			List<String> templates = metadata.templates();
			if (templates.contains(fileTemplate) || (file instanceof CsvFile && templates.contains("csv"))) {
				result.add(metadata);
			}
		}
		return result;
	}

	private void restoreScriptSelection() {
		String templateName = Templates.getTemplateName(selectedFile);
		String selectedScriptFilename = preferences.getString(PREFS_NODE_NAME, PREFS_LAST_SELECTED_SCRIPT_PREFIX + "_" + templateName);
		ScriptMetadata selectedScriptMetadata = scriptsMetadata.stream()
				.filter(scriptMetadata -> scriptMetadata.filename().equals(selectedScriptFilename))
				.findAny()
				.orElse(null);
		scriptsMetadataSelector.getItems().setAll(scriptsMetadata);
		if (selectedScriptMetadata != null && scriptsMetadataSelector.getItems().contains(selectedScriptMetadata)) {
			scriptsMetadataSelector.getSelectionModel().select(selectedScriptMetadata);
		} else {
			scriptsMetadataSelector.getSelectionModel().clearSelection();
			scriptsMetadataSelector.setValue(null);
			scriptsMetadataSelector.setPromptText("Select script");
		}
	}

	private void refreshExecutionStatus(@Nullable SgyFile file) {
		if (scriptRunner.isExecuting(file)) {
            disableAndShowProgress();
			ScriptMetadata executingScriptMetadata = scriptRunner.getExecutingScriptMetadata(file);
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
		String labelText = param.displayName() + rangeHint(param) + (param.required() ? " *" : "");
		Node inputNode = getInputNode(param, initialValue, labelText);
		if (param.type() != ScriptParameter.ParameterType.BOOLEAN) {
			paramBox.getChildren().add(new Label(labelText));
		}
		paramBox.getChildren().add(inputNode);
		return paramBox;
	}

	private static String rangeHint(ScriptParameter param) {
		Double min = param.min();
		Double max = param.max();
		if (min == null && max == null) {
			return "";
		}
		String minStr = min != null ? formatBound(min) : "…";
		String maxStr = max != null ? formatBound(max) : "…";
		return " (" + minStr + "–" + maxStr + ")";
	}

	private static String formatBound(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	private Node getInputNode(ScriptParameter param, String initialValue, String labelText) {
		return switch (param.type()) {
			case STRING, FILE_PATH -> createTextField(param, initialValue);
			case INTEGER -> createIntegerField(param, initialValue);
			case DOUBLE -> createDoubleField(param, initialValue);
			case BOOLEAN -> createCheckBox(param, initialValue, labelText);
			case COLUMN_NAME -> createColumnSelector(param, initialValue);
			case FOLDER_PATH -> createFolderPathSelector(param, initialValue);
			case ENUM -> createEnumSelector(param, initialValue);
		};
	}

	private static TextField createTextField(ScriptParameter param, String initialValue) {
		TextField textField = new TextField(initialValue);
		textField.setUserData(param);
		textField.setPromptText(param.displayName());
		return textField;
	}

	private static TextField createIntegerField(ScriptParameter param, String initialValue) {
		TextField textField = new TextField(initialValue);
		textField.setUserData(param);
		textField.setPromptText("Enter integer value");
		return textField;
	}

	private static TextField createDoubleField(ScriptParameter param, String initialValue) {
		TextField textField = new TextField(initialValue);
		textField.setUserData(param);
		textField.setPromptText("Enter decimal value");
		return textField;
	}

	private static CheckBox createCheckBox(ScriptParameter param, String initialValue, String labelText) {
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
			updateComboBoxIfChanged(columnSelector, getAvailableColumnsForFile(csvFile), initialValue);
		}
		return columnSelector;
	}

	private static ComboBox<String> createEnumSelector(ScriptParameter param, String initialValue) {
		ComboBox<String> selector = new ComboBox<>();
		selector.setMaxWidth(Double.MAX_VALUE);
		selector.setUserData(param);
		List<String> values = param.enumValues() != null ? param.enumValues() : List.of();
		selector.getItems().addAll(values);
		String selected = (!initialValue.isEmpty() && values.contains(initialValue))
				? initialValue
				: (!values.isEmpty() ? values.getFirst() : null);
		selector.setValue(selected);
		return selector;
	}

	private HBox createFolderPathSelector(ScriptParameter param, String initialValue) {
		HBox container = new HBox(5);
		container.setUserData(param);

		String defaultFolderPath = initialValue;
		if (Strings.isNullOrEmpty(defaultFolderPath) && selectedFile != null && selectedFile.getFile() != null) {
			File parentDir = selectedFile.getFile().getParentFile();
			if (parentDir != null) {
				defaultFolderPath = parentDir.getAbsolutePath();
			}
		}

		TextField pathField = new TextField(defaultFolderPath);
		pathField.setPromptText("Select folder...");
		HBox.setHgrow(pathField, Priority.ALWAYS);

		Button selectButton = new Button("Select");
		selectButton.setOnAction(e -> {
			DirectoryChooser directoryChooser = new DirectoryChooser();
			directoryChooser.setTitle("Select Folder");
			String currentPath = pathField.getText();
			if (!Strings.isNullOrEmpty(currentPath)) {
				File initialDir = new File(currentPath);
				if (initialDir.exists() && initialDir.isDirectory()) {
					directoryChooser.setInitialDirectory(initialDir);
				}
			}
			File selectedDirectory = directoryChooser.showDialog(selectButton.getScene().getWindow());
			if (selectedDirectory != null) {
				pathField.setText(selectedDirectory.getAbsolutePath());
			}
		});

		container.getChildren().addAll(pathField, selectButton);
		return container;
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

		Consumer<String> onScriptOutput = line -> {
			if (!Strings.isNullOrEmpty(line)) {
				status.showMessage(line, scriptMetadata.displayName());
			}
		};

		Platform.runLater(this::disableAndShowProgress);

		Future<Void> future = executor.submit(() -> {
			scriptRunner.run(files, scriptMetadata, parameters, onScriptOutput, this);
			Platform.runLater(this::enableAndHideProgress);
			return null;
		});

		taskService.registerTask(future, buildTaskName(scriptMetadata, files));
	}

	private String buildTaskName(ScriptMetadata scriptMetadata, List<SgyFile> files) {
		String taskName = "Running script " + scriptMetadata.displayName();
		if (files.size() == 1) {
			SgyFile sgyFile = files.getFirst();
			if (sgyFile.getFile() != null) {
				taskName += ": " + sgyFile.getFile().getName();
			}
		} else if (files.size() > 1) {
			taskName += " on " + files.size() + " files";
		}
		return taskName;
	}

	@Override
	public void onSuccess(ScriptMetadata metadata) {
		status.showMessage("Script executed successfully.", metadata.displayName());
	}

	@Override
	public void onError(ScriptMetadata metadata, Exception e, String scriptOutput) {
		showError(metadata, e, scriptOutput);
	}

	@Override
	public boolean confirmReinstallDependencies(String moduleName) {
		if (Platform.isFxApplicationThread()) {
			return showReinstallDialog(moduleName);
		}
		CompletableFuture<Boolean> answer = new CompletableFuture<>();
		Platform.runLater(() -> answer.complete(showReinstallDialog(moduleName)));
		try {
			return answer.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (ExecutionException e) {
			log.error("Reinstall confirmation dialog failed", e);
			return false;
		}
	}

	private boolean showReinstallDialog(String moduleName) {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle("Reinstall Dependencies");
		alert.setHeaderText("Dependency issue detected");
		String reason = "Module '" + moduleName + "' is installed but cannot be imported.";
		Label content = new Label(reason + "\n\nForce reinstall dependencies?");
		content.setWrapText(true);
		content.setPrefWidth(400);
		alert.getDialogPane().setContent(content);
		alert.initOwner(AppContext.stage);
		Optional<ButtonType> result = alert.showAndWait();
		return result.isPresent() && result.get() == ButtonType.OK;
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
		Dialogs.showError("Script Execution Error", message);
	}

	private Map<String, String> extractScriptParams() {
		Map<String, String> parameters = new HashMap<>();
		for (Node paramBox : parametersBox.getChildren()) {
			if (!(paramBox instanceof VBox vbox)) {
				continue;
			}
			for (Node inputNode : vbox.getChildren()) {
				if (!(inputNode.getUserData() instanceof ScriptParameter param)) {
					continue;
				}
				String value = extractValueFromNode(inputNode);
				if (value != null) {
					parameters.put(param.name(), value);
				}
			}
		}
		return parameters;
	}

	@Nullable
	private static String extractValueFromNode(Node node) {
		return switch (node) {
			case TextField textField -> textField.getText();
			case CheckBox checkBox -> String.valueOf(checkBox.isSelected());
			case ComboBox<?> comboBox -> {
				Object value = comboBox.getValue();
				yield value != null ? value.toString() : "";
			}
			case HBox hBox -> hBox.getChildren().stream()
					.filter(TextField.class::isInstance)
					.map(TextField.class::cast)
					.findFirst()
					.map(TextField::getText)
					.orElse("");
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
		for (Node paramBox : parametersBox.getChildren()) {
			if (!(paramBox instanceof VBox vbox)) {
				continue;
			}
			for (Node inputNode : vbox.getChildren()) {
				if (inputNode instanceof ComboBox<?> comboBox
						&& comboBox.getUserData() instanceof ScriptParameter param
						&& param.type() == ScriptParameter.ParameterType.COLUMN_NAME) {
					updateComboBoxIfChanged((ComboBox<String>) comboBox, availableColumns, null);
				}
			}
		}
	}

	private void updateComboBoxIfChanged(ComboBox<String> comboBox, Set<String> availableColumns,
			@Nullable String initialValue) {
		if (availableColumns.isEmpty()) {
			comboBox.setPromptText("No columns available");
			comboBox.setDisable(true);
			return;
		}
		comboBox.setDisable(false);
		Set<String> currentItems = new HashSet<>(comboBox.getItems());
		if (currentItems.equals(availableColumns)) {
			return;
		}
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
