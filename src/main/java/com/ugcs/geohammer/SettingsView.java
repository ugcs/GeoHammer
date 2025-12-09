package com.ugcs.geohammer;

import com.ugcs.geohammer.view.MessageBoxHelper;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.service.script.PythonConfig;
import com.ugcs.geohammer.model.ToolProducer;
import com.ugcs.geohammer.util.PythonLocator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

@Component
public class SettingsView implements ToolProducer {

	private static final String PREF_PYTHON_EXECUTOR = "python_executor";

	private static final String PREF_PYTHON_EXECUTOR_PATH = "path";

	private final PrefSettings prefSettings;

	private final PythonConfig pythonConfig;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	@Nullable
	private Stage settingsStage = null;

	@Nullable
	private TextField pythonPathField = null;

	private String originalPythonPath = "";

	private final ToggleButton toggleButton =
			ResourceImageHolder.setButtonImage(ResourceImageHolder.SETTINGS, new ToggleButton());

	public SettingsView(PrefSettings prefSettings, PythonConfig pythonConfig) {
		this.prefSettings = prefSettings;
		this.pythonConfig = pythonConfig;

		toggleButton.setTooltip(new Tooltip("Settings"));
		toggleButton.setSelected(false);
		toggleButton.setOnAction(event -> {
			if (toggleButton.isSelected()) {
				showSettingsWindow();
			} else {
				hideSettingsWindow();
			}
		});
	}

	@Override
	public List<Node> getToolNodes() {
		return List.of(toggleButton);
	}

	private void showSettingsWindow() {
		Platform.runLater(() -> {
			if (settingsStage == null) {
				settingsStage = createSettingsStage();
				settingsStage.initOwner(AppContext.stage);
				settingsStage.setOnHiding(event -> onClose(settingsStage));
			}
			settingsStage.show();
		});
	}

	private void hideSettingsWindow() {
		if (settingsStage != null) {
			settingsStage.hide();
		}
	}

	private Stage createSettingsStage() {
		Stage stage = new Stage();
		stage.setTitle("Settings");

		stage.setOnCloseRequest(event -> toggleButton.setSelected(false));

		Node pythonPathSetting = createPythonPathPane(stage);

		HBox buttonsRow = createButtonsRow(stage);

		VBox root = new VBox(10, pythonPathSetting, buttonsRow);
		Scene scene = new Scene(root, 700, 120);

		stage.setScene(scene);
		return stage;
	}

	private Node createPythonPathPane(Stage settingsStage) {
		Label pythonLabel = new Label("Python Executor Path:");
		pythonPathField = new TextField();
		pythonPathField.setEditable(false);
		pythonPathField.setPrefWidth(400);

		String pythonPath = "";
		@Nullable String configPath = pythonConfig.getPythonExecutorPath();
		@Nullable String prefPath = loadPythonExecutorPath();
		if (configPath != null && !configPath.isEmpty()) {
			pythonPath = configPath;
		} else if (prefPath != null && !prefPath.isEmpty()) {
			pythonPath = prefPath;
		} else {
			try {
				Future<String> future = executor.submit(PythonLocator::getPythonExecutorPath);
				pythonPath = future.get();
			} catch (Exception e) {
				MessageBoxHelper.showError("Error", "Could not determine Python executable path: " + e.getMessage());
			}
		}

		pythonPathField.setText(pythonPath);
		originalPythonPath = pythonPath;

		return createPythonPathRow(settingsStage, pythonLabel);
	}

	private @Nullable String loadPythonExecutorPath() {
		return prefSettings.getString(PREF_PYTHON_EXECUTOR, PREF_PYTHON_EXECUTOR_PATH);
	}

	private void savePythonExecutorPath(@Nullable String pythonPath) {
		if (pythonPath != null && !pythonPath.isEmpty()) {
			prefSettings.setValue(PREF_PYTHON_EXECUTOR, PREF_PYTHON_EXECUTOR_PATH, pythonPath);
		}
	}

	private @NotNull HBox createPythonPathRow(Stage settingsStage, Label pythonLabel) {
		Button browseButton = new Button("Browse...");
		browseButton.setOnAction(event -> onBrowseClicked(settingsStage));

		Button pasteButton = ResourceImageHolder.setButtonImage(ResourceImageHolder.PASTE, new Button());
		pasteButton.setTooltip(new Tooltip("Paste path from clipboard"));
		pasteButton.setOnAction(event -> onPasteClicked());

		HBox row = new HBox(10, pythonLabel, pythonPathField, pasteButton, browseButton);
		row.setPadding(new Insets(20, 20, 20, 20));
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private void onBrowseClicked(Stage settingsStage) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Python Executable");
		File file = fileChooser.showOpenDialog(settingsStage);
		if (file != null) {
			String path = file.getAbsolutePath();
			if (pythonPathField != null) {
				pythonPathField.setText(path);
			}
		}
	}

	private void onPasteClicked() {
		Clipboard clipboard = Clipboard.getSystemClipboard();
		if (clipboard.hasString()) {
			String path = clipboard.getString();
			if (pythonPathField != null) {
				pythonPathField.setText(path);
			}
		}
	}

	private HBox createButtonsRow(Stage settingsStage) {
		Button okButton = new Button("OK");
		okButton.setPrefWidth(60);
		okButton.setOnAction(event -> onSave(settingsStage));

		Button closeButton = new Button("Close");
		closeButton.setPrefWidth(60);
		closeButton.setOnAction(event -> onClose(settingsStage));

		HBox buttonsBox = new HBox(10, okButton, closeButton);
		buttonsBox.setAlignment(Pos.CENTER_RIGHT);
		buttonsBox.setPadding(new Insets(0, 20, 20, 20));
		return buttonsBox;
	}

	private void onSave(Stage settingsStage) {
		if (pythonPathField != null) {
			String path = pythonPathField.getText();
			savePythonExecutorPath(path);
			pythonConfig.setPythonExecutorPath(path);
			originalPythonPath = path;
		}
		toggleButton.setSelected(false);
		settingsStage.close();
	}

	private void onClose(Stage settingsStage) {
		if (pythonPathField != null) {
			pythonPathField.setText(originalPythonPath);
			pythonConfig.setPythonExecutorPath(originalPythonPath);
		}
		toggleButton.setSelected(false);
		settingsStage.close();
	}
}
