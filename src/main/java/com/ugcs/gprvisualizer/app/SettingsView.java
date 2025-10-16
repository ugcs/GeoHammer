package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.app.scripts.PythonConfig;
import com.ugcs.gprvisualizer.draw.Layer;
import com.ugcs.gprvisualizer.utils.PythonLocator;
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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

@Component
public class SettingsView implements Layer, InitializingBean {

	private final PythonConfig pythonConfig;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	@Nullable
	private Stage settingsStage = null;

	private final ToggleButton toggleButton =
			ResourceImageHolder.setButtonImage(ResourceImageHolder.SETTINGS, new ToggleButton());
	{
		toggleButton.setTooltip(new Tooltip("Settings"));
	}

	public SettingsView(PythonConfig pythonConfig) {
		this.pythonConfig = pythonConfig;
	}

	public List<Node> buildToolNodes() {
		toggleButton.setSelected(false);
		toggleButton.setOnAction(event -> {
			if (toggleButton.isSelected()) {
				showSettingsWindow();
			} else {
				hideSettingsWindow();
			}
		});
		return List.of(toggleButton);
	}

	private void showSettingsWindow() {
		Platform.runLater(() -> {
			if (settingsStage == null) {
				settingsStage = createSettingsStage();
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

		VBox root = new VBox(pythonPathSetting);
		Scene scene = new Scene(root, 700, 120);

		stage.setScene(scene);
		return stage;
	}


	private Node createPythonPathPane(Stage settingsStage) {
		Label pythonLabel = new Label("Python Executor Path:");
		TextField pythonPathField = new TextField();
		pythonPathField.setEditable(false);
		pythonPathField.setPrefWidth(400);

		String pythonPath = "";
		String configPath = pythonConfig.getPythonExecutorPath();
		if (configPath != null && !configPath.isEmpty()) {
			pythonPath = configPath;
		} else {
			try {
				Future<String> future = executor.submit(PythonLocator::getPythonExecutorPath);
				pythonPath = future.get();
			} catch (Exception e) {
				MessageBoxHelper.showError("Error", "Could not determine Python executable path: " + e.getMessage());
			}
		}

		pythonPathField.setText(pythonPath);

		return createPythonPathRow(settingsStage, pythonPathField, pythonLabel);
	}

	private @NotNull HBox createPythonPathRow(Stage settingsStage, TextField pythonPathField, Label pythonLabel) {
		Button browseButton = new Button("Browse...");
		browseButton.setOnAction(event -> onBrowseClicked(settingsStage, pythonPathField));

		Button pasteButton = ResourceImageHolder.setButtonImage(ResourceImageHolder.PASTE, new Button());
		pasteButton.setTooltip(new Tooltip("Paste path from clipboard"));
		pasteButton.setOnAction(event -> onPasteClicked(pythonPathField));

		HBox row = new HBox(10, pythonLabel, pythonPathField, pasteButton, browseButton);
		row.setPadding(new Insets(20, 20, 20, 20));
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private void onBrowseClicked(Stage settingsStage, TextField pythonPathField) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Python Executable");
		File file = fileChooser.showOpenDialog(settingsStage);
		if (file != null) {
			pythonPathField.setText(file.getAbsolutePath());
			pythonConfig.setPythonExecutorPath(file.getAbsolutePath());
		}
	}

	private void onPasteClicked(TextField pythonPathField) {
		Clipboard clipboard = Clipboard.getSystemClipboard();
		if (clipboard.hasString()) {
			String path = clipboard.getString();
			pythonPathField.setText(path);
			pythonConfig.setPythonExecutorPath(path);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// No initialization needed
	}

	@Override
	public void draw(Graphics2D g2, MapField field) {
		// No drawing needed for settings
	}
}
