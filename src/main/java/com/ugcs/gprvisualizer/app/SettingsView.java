package com.ugcs.gprvisualizer.app;

import com.ugcs.gprvisualizer.app.scripts.PythonConfig;
import com.ugcs.gprvisualizer.utils.PythonLocator;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.Future;

public class SettingsView extends VBox {

	private static final Logger log = LoggerFactory.getLogger(SettingsView.class);

	private final PythonConfig pythonConfig;

	public SettingsView(PythonConfig pythonConfig) {
		this.pythonConfig = pythonConfig;

		Stage settingsStage = new Stage();
		settingsStage.setTitle("Settings");

		Node pythonPathSetting = createPythonPathSetting(settingsStage);

		VBox root = new VBox(pythonPathSetting);
		Scene scene = new Scene(root, 500, 120);

		settingsStage.setScene(scene);
		settingsStage.show();
	}

	private Node createPythonPathSetting(Stage settingsStage) {
		Label pythonLabel = new Label("Python Executor Path:");
		TextField pythonPathField = new TextField();
		pythonPathField.setPrefWidth(200);

		String pythonPath = "";
		String configPath = pythonConfig.getPythonExecutorPath();
		if (configPath != null && !configPath.isEmpty()) {
			pythonPath = configPath;
		} else {
			Future<String> future = new PythonLocator().getPythonExecutorPathAsync();
			try {
				pythonPath = future.get();
			} catch (Exception e) {
				log.error("Error getting Python path", e);
			}
		}

		pythonPathField.setText(pythonPath);

		return createPythonPathRow(settingsStage, pythonPathField, pythonLabel);
	}

	private @NotNull HBox createPythonPathRow(Stage settingsStage, TextField pythonPathField, Label pythonLabel) {
		Button browseButton = new Button("Browse...");
		browseButton.setOnAction(e -> {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Select Python Executable");
			File file = fileChooser.showOpenDialog(settingsStage);
			if (file != null) {
				pythonPathField.setText(file.getAbsolutePath());
				pythonConfig.setPythonExecutorPath(file.getAbsolutePath());
			}
		});

		HBox row = new HBox(10, pythonLabel, pythonPathField, browseButton);
		row.setPadding(new Insets(20, 20, 20, 20));
		return row;
	}

}
