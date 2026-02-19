package com.ugcs.geohammer;

import java.io.File;
import java.util.List;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.ToolProducer;
import com.ugcs.geohammer.service.script.PythonService;
import com.ugcs.geohammer.view.MessageBoxHelper;
import com.ugcs.geohammer.view.ResourceImageHolder;
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

import javax.annotation.Nullable;

@Component
public class SettingsView implements ToolProducer {

	private static final String PREF_TRACE = "trace";

	private static final String PREF_LOOKUP_THRESHOLD = "lookupThreshold";

	private static final double DEFAULT_LOOKUP_THRESHOLD = 1.0;

	private final PythonService pythonService;

	private final PrefSettings prefSettings;

	private final Model model;

	@Nullable
	private Stage settingsStage = null;

	@Nullable
	private TextField pythonPathField = null;

	@Nullable
	private TextField traceLookupThresholdField = null;

	private String savedPythonPath = "";

	private double savedTraceLookupThreshold;

	private final ToggleButton toggleButton =
			ResourceImageHolder.setButtonImage(ResourceImageHolder.SETTINGS, new ToggleButton());

	public SettingsView(PythonService pythonService, PrefSettings prefSettings, Model model) {
		this.pythonService = pythonService;
		this.prefSettings = prefSettings;
		this.model = model;
		model.setTraceLookupThreshold(
				prefSettings.getDoubleOrDefault(PREF_TRACE, PREF_LOOKUP_THRESHOLD, DEFAULT_LOOKUP_THRESHOLD));

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
		Node traceLookupThresholdSetting = createTraceLookupThresholdPane();

		HBox buttonsRow = createButtonsRow(stage);

		VBox root = new VBox(10, pythonPathSetting, traceLookupThresholdSetting, buttonsRow);
		Scene scene = new Scene(root, 700, 160);

		stage.setScene(scene);
		return stage;
	}

	private Node createPythonPathPane(Stage settingsStage) {
		Label pythonLabel = new Label("Python Executor Path:");
		pythonPathField = new TextField();
		pythonPathField.setEditable(false);
		pythonPathField.setPrefWidth(400);

		String pythonPath = "";
		try {
			pythonPath = pythonService.getPythonPath().toString();
		} catch (Exception e) {
			MessageBoxHelper.showError("Error", "Could not determine Python executable path: " + e.getMessage());
		}

		pythonPathField.setText(pythonPath);
		savedPythonPath = pythonPath;

		return createPythonPathRow(settingsStage, pythonLabel);
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

	private Node createTraceLookupThresholdPane() {
		Label label = new Label("Trace Lookup Threshold (m):");
		traceLookupThresholdField = new TextField();
		traceLookupThresholdField.setPrefWidth(80);
		savedTraceLookupThreshold = prefSettings.getDoubleOrDefault(PREF_TRACE, PREF_LOOKUP_THRESHOLD, DEFAULT_LOOKUP_THRESHOLD);
		traceLookupThresholdField.setText(String.valueOf(savedTraceLookupThreshold));

		HBox row = new HBox(10, label, traceLookupThresholdField);
		row.setPadding(new Insets(0, 20, 0, 20));
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
		double threshold = savedTraceLookupThreshold;
		if (traceLookupThresholdField != null) {
			try {
				threshold = Double.parseDouble(traceLookupThresholdField.getText());
				if (threshold < 0) {
					MessageBoxHelper.showError("Invalid Input", "Trace lookup threshold must be a non-negative number.");
					return;
				}
			} catch (NumberFormatException e) {
				MessageBoxHelper.showError("Invalid Input", "Trace lookup threshold must be a valid number.");
				return;
			}
		}
		if (pythonPathField != null) {
			String path = pythonPathField.getText();
			pythonService.setPythonPath(path);
			savedPythonPath = path;
		}
		if (traceLookupThresholdField != null) {
			prefSettings.setValue(PREF_TRACE, PREF_LOOKUP_THRESHOLD, threshold);
			model.setTraceLookupThreshold(threshold);
			savedTraceLookupThreshold = threshold;
		}
		toggleButton.setSelected(false);
		settingsStage.close();
	}

	private void onClose(Stage settingsStage) {
		if (pythonPathField != null) {
			pythonPathField.setText(savedPythonPath);
			pythonService.setPythonPath(savedPythonPath);
		}
		if (traceLookupThresholdField != null) {
			traceLookupThresholdField.setText(String.valueOf(savedTraceLookupThreshold));
		}
		toggleButton.setSelected(false);
		settingsStage.close();
	}
}
