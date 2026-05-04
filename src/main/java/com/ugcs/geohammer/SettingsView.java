package com.ugcs.geohammer;

import java.io.File;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.ToolProducer;
import com.ugcs.geohammer.service.script.PythonService;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.style.Theme;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@Component
public class SettingsView implements ToolProducer {

	private static final String PREF_TRACE = "trace";

	private static final String PREF_LOOKUP_THRESHOLD = "lookupThreshold";

	private final PythonService pythonService;

	private final PrefSettings prefSettings;

	private final ThemeService themeService;

	private final Model model;

	@Nullable
	private Stage settingsStage = null;

	@Nullable
	private TextField pythonPathField = null;

	@Nullable
	private TextField traceLookupThresholdField = null;

	@Nullable
	private ComboBox<Theme> themeSelector = null;

	private String savedPythonPath = "";

	private double savedTraceLookupThreshold;

	private final ToggleButton toggleButton =
			ResourceImageHolder.setButtonImage(ResourceImageHolder.SETTINGS, new ToggleButton());

	public SettingsView(PythonService pythonService, PrefSettings prefSettings, ThemeService themeService, Model model) {
		this.pythonService = pythonService;
		this.prefSettings = prefSettings;
		this.themeService = themeService;
		this.model = model;
		model.setTraceLookupThreshold(
				prefSettings.getDoubleOrDefault(PREF_TRACE, PREF_LOOKUP_THRESHOLD, Model.DEFAULT_LOOKUP_THRESHOLD));

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
	public ToolNodes getToolNodes() {
		return ToolNodes.of(toggleButton);
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
		Node themeSetting = createThemePane();

		HBox buttonsRow = createButtonsRow(stage);

		VBox root = new VBox(10,
				themeSetting,
				pythonPathSetting,
				traceLookupThresholdSetting,
				Views.createSpacer(),
				buttonsRow);
		root.setPadding(new Insets(20));
		Scene scene = new Scene(root, 500, 200);
		stage.setScene(scene);
		themeService.registerScene(scene);
		return stage;
	}

	private Node createPythonPathPane(Stage settingsStage) {
		Label pythonLabel = new Label("Python executable");
		HBox.setHgrow(pythonLabel, Priority.ALWAYS);

		pythonPathField = new TextField();
		pythonPathField.setEditable(false);
		HBox.setHgrow(pythonPathField, Priority.ALWAYS);

		String pythonPath = "";
		try {
			pythonPath = pythonService.getPythonPath().toString();
		} catch (Exception e) {
			Dialogs.showError("Could not determine Python executable path", e);
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
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private Node createTraceLookupThresholdPane() {
		Label label = new Label("Trace lookup threshold (m)");
		HBox.setHgrow(label, Priority.ALWAYS);
		traceLookupThresholdField = new TextField();
		traceLookupThresholdField.setPrefWidth(80);
		savedTraceLookupThreshold = prefSettings.getDoubleOrDefault(PREF_TRACE, PREF_LOOKUP_THRESHOLD, Model.DEFAULT_LOOKUP_THRESHOLD);
		traceLookupThresholdField.setText(String.valueOf(savedTraceLookupThreshold));

		HBox row = new HBox(10, label, traceLookupThresholdField);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private Node createThemePane() {
		Label label = new Label("Theme");
		themeSelector = new ComboBox<>();
		themeSelector.setConverter(new ThemeTitleConverter());
		themeSelector.getItems().addAll(Theme.values());
		themeSelector.setValue(themeService.getTheme());

		HBox row = new HBox(10, label, themeSelector);
		row.setAlignment(Pos.CENTER_LEFT);
		return row;
	}

	private void onBrowseClicked(Stage settingsStage) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Python executable");
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

		HBox buttonsBox = new HBox(10, closeButton, okButton);
		buttonsBox.setAlignment(Pos.CENTER_RIGHT);
		return buttonsBox;
	}

	private void onSave(Stage settingsStage) {
		double threshold = savedTraceLookupThreshold;
		if (traceLookupThresholdField != null) {
			try {
				threshold = Double.parseDouble(traceLookupThresholdField.getText());
				if (threshold < 0) {
					Dialogs.showError("Invalid Input", "Trace lookup threshold must be a non-negative number.");
					return;
				}
			} catch (NumberFormatException e) {
				Dialogs.showError("Invalid Input", "Trace lookup threshold must be a valid number.", e);
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
		if (themeSelector != null) {
			Theme theme = themeSelector.getValue();
			themeService.setTheme(theme);
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
		if (themeSelector != null) {
			themeSelector.setValue(themeService.getTheme());
		}
		toggleButton.setSelected(false);
		settingsStage.close();
	}

	static class ThemeTitleConverter extends StringConverter<Theme> {

		@Override
		public String toString(Theme theme) {
			return theme == null ? null : theme.title();
		}

		@Override
		public Theme fromString(String title) {
			return Theme.findByTitle(title);
		}
	}
}
