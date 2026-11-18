package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.service.script.PythonInterpreter;
import com.ugcs.geohammer.service.script.ScriptPaths;
import com.ugcs.geohammer.service.script.UserScriptsFolder;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.control.NodeWithOverlay;
import com.ugcs.geohammer.view.control.NodeWithTopLabel;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.controlsfx.validation.ValidationResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

@Component
public class ScriptsTab implements SettingsTab {

    private static final Logger log = LoggerFactory.getLogger(ScriptsTab.class);

    private final PythonInterpreter pythonInterpreter;

    private final ScriptPaths scriptPaths;

    private final VBox content;

    private final TextField pythonPathInput;

    private final TextField userScriptsFolderInput;

    public ScriptsTab(PythonInterpreter pythonInterpreter, ScriptPaths scriptPaths) {
        this.pythonInterpreter = Check.notNull(pythonInterpreter);
        this.scriptPaths = Check.notNull(scriptPaths);

        pythonPathInput = new TextField();

        Button browse = new Button("Browse...");
        browse.setOnAction(event -> onPythonPathBrowse());

        Button paste = Views.createSvgButton(
                ResourceImageHolder.PASTE,
                24,
                "Paste path from clipboard");
        paste.setOnAction(event -> onPythonPathPaste());

        NodeWithOverlay<TextField> pythonPathWithPaste = new NodeWithOverlay<>(pythonPathInput, paste);
        HBox.setHgrow(pythonPathWithPaste, Priority.ALWAYS);

        HBox pythonPathWithButtons = new HBox(Views.DEFAULT_SPACING, pythonPathWithPaste, browse);
        pythonPathWithButtons.setAlignment(Pos.CENTER_LEFT);

        NodeWithTopLabel<HBox> pythonPathWithLabel = new NodeWithTopLabel<>(
                "Python executable path", pythonPathWithButtons);

        userScriptsFolderInput = new TextField();
        userScriptsFolderInput.setPromptText(UserScriptsFolder.getDefaultPath().toString());
        HBox.setHgrow(userScriptsFolderInput, Priority.ALWAYS);

        Button userScriptsFolderBrowseButton = new Button("Browse...");
        userScriptsFolderBrowseButton.setOnAction(event -> onUserScriptsFolderBrowse());

        HBox userScriptsFolderWithBrowse = new HBox(Views.DEFAULT_SPACING,
                userScriptsFolderInput, userScriptsFolderBrowseButton);
        userScriptsFolderWithBrowse.setAlignment(Pos.CENTER_LEFT);

        NodeWithTopLabel<HBox> userScriptsFolderWithLabel = new NodeWithTopLabel<>(
                "User scripts folder", userScriptsFolderWithBrowse);

        content = new VBox(SettingsView.VERTICAL_SPACING, pythonPathWithLabel, userScriptsFolderWithLabel);
    }

    private void onPythonPathBrowse() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Python executable");
        File file = fileChooser.showOpenDialog(AppContext.stage);
        if (file != null) {
            pythonPathInput.setText(file.getAbsolutePath());
        }
    }

    private void onPythonPathPaste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasString()) {
            pythonPathInput.setText(clipboard.getString());
        }
    }

    private void onUserScriptsFolderBrowse() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select scripts folder");
        File directory = scriptPaths.getUserScriptsPath().toFile();
        if (directory.isDirectory()) {
            directoryChooser.setInitialDirectory(directory);
        }
        File selected = directoryChooser.showDialog(AppContext.stage);
        if (selected != null) {
            userScriptsFolderInput.setText(selected.getAbsolutePath());
        }
    }

    private @Nullable Path readUserScriptsFolderInput() {
        String text = Strings.trim(userScriptsFolderInput.getText());
        return Strings.isNullOrEmpty(text)
                ? null
                : Path.of(text).toAbsolutePath().normalize();
    }

    @Override
    public Tab getTab() {
        return new  Tab("Scripts", content);
    }

    @Override
    public void load() {
        Path path = null;
        try {
            path = pythonInterpreter.getPath();
        } catch (IOException ignore) {
        }
        pythonPathInput.setText(path != null ? path.toString() : Strings.empty());
        userScriptsFolderInput.setText(scriptPaths.getUserScriptsPath().toString());
    }

    @Override
    public ValidationResult validate() {
        try {
            Path path = readUserScriptsFolderInput();
            if (path != null && !path.equals(scriptPaths.getUserScriptsPath()) && !Files.isDirectory(path)) {
                return ValidationResult.fromError(userScriptsFolderInput, "User scripts folder does not exist");
            }
        } catch (InvalidPathException e) {
            return ValidationResult.fromError(userScriptsFolderInput, "User scripts folder must be a valid path");
        }
        return new ValidationResult();
    }

    @Override
    public void save() {
        String path = pythonPathInput.getText();
        pythonInterpreter.setPath(path != null ? path.trim() : null);
        Path userScriptsFolderPath = readUserScriptsFolderInput();
        try {
            scriptPaths.setUserScriptsPath(userScriptsFolderPath);
        } catch (RuntimeException e) {
            log.error("Cannot change scripts folder to {}", userScriptsFolderPath, e);
        }
    }
}
