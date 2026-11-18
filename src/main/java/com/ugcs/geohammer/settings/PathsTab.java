package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.template.UserTemplatesFolder;
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
import java.util.concurrent.ExecutorService;

@Component
public class PathsTab implements SettingsTab {

    private static final Logger log = LoggerFactory.getLogger(PathsTab.class);

    private static final String USER_SCRIPTS_FOLDER = "User scripts folder";

    private static final String USER_TEMPLATES_FOLDER = "User templates folder";

    private final PythonInterpreter pythonInterpreter;

    private final ScriptPaths scriptPaths;

    private final FileTemplates fileTemplates;

    private final ExecutorService executor;

    private final VBox content;

    private final TextField pythonPathInput;

    private final TextField userScriptsFolderInput;

    private final TextField userTemplatesFolderInput;

    public PathsTab(PythonInterpreter pythonInterpreter, ScriptPaths scriptPaths, FileTemplates fileTemplates,
            ExecutorService executor) {
        this.pythonInterpreter = Check.notNull(pythonInterpreter);
        this.scriptPaths = Check.notNull(scriptPaths);
        this.fileTemplates = Check.notNull(fileTemplates);
        this.executor = Check.notNull(executor);

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
                "Python executable", pythonPathWithButtons);

        userScriptsFolderInput = new TextField();
        userScriptsFolderInput.setPromptText(UserScriptsFolder.getDefaultPath().toString());

        userTemplatesFolderInput = new TextField();
        userTemplatesFolderInput.setPromptText(UserTemplatesFolder.getDefaultPath().toString());

        content = new VBox(SettingsView.VERTICAL_SPACING,
                pythonPathWithLabel,
                createFolderRow(USER_SCRIPTS_FOLDER, userScriptsFolderInput,
                        () -> onFolderBrowse(userScriptsFolderInput, scriptPaths.getUserScriptsPath())),
                createFolderRow(USER_TEMPLATES_FOLDER, userTemplatesFolderInput,
                        () -> onFolderBrowse(userTemplatesFolderInput, fileTemplates.getUserTemplatesPath())));
    }

    @Override
    public Tab getTab() {
        return new Tab("Paths", content);
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
        userTemplatesFolderInput.setText(fileTemplates.getUserTemplatesPath().toString());
    }

    @Override
    public ValidationResult validate() {
        return validateFolder(userScriptsFolderInput, scriptPaths.getUserScriptsPath(), USER_SCRIPTS_FOLDER)
                .combine(validateFolder(userTemplatesFolderInput, fileTemplates.getUserTemplatesPath(),
                        USER_TEMPLATES_FOLDER));
    }

    @Override
    public void save() {
        String path = pythonPathInput.getText();
        pythonInterpreter.setPath(path != null ? path.trim() : null);

        Path userScriptsFolderPath = readFolderInput(userScriptsFolderInput);
        try {
            scriptPaths.setUserScriptsPath(userScriptsFolderPath);
        } catch (RuntimeException e) {
            log.error("Cannot change scripts folder to {}", userScriptsFolderPath, e);
        }

        // templates are reloaded on a folder change, so it is applied in background
        Path userTemplatesFolderPath = readFolderInput(userTemplatesFolderInput);
        executor.submit(() -> {
            try {
                fileTemplates.setUserTemplatesPath(userTemplatesFolderPath);
            } catch (RuntimeException e) {
                log.error("Cannot change templates folder to {}", userTemplatesFolderPath, e);
            }
        });
    }

    private static NodeWithTopLabel<HBox> createFolderRow(String label, TextField input, Runnable onBrowse) {
        HBox.setHgrow(input, Priority.ALWAYS);

        Button browse = new Button("Browse...");
        browse.setOnAction(event -> onBrowse.run());

        HBox inputWithBrowse = new HBox(Views.DEFAULT_SPACING, input, browse);
        inputWithBrowse.setAlignment(Pos.CENTER_LEFT);

        return new NodeWithTopLabel<>(label, inputWithBrowse);
    }

    private static ValidationResult validateFolder(TextField input, Path currentPath, String label) {
        try {
            Path path = readFolderInput(input);
            if (path != null && !path.equals(currentPath) && !Files.isDirectory(path)) {
                return ValidationResult.fromError(input, label + " does not exist");
            }
        } catch (InvalidPathException e) {
            return ValidationResult.fromError(input, label + " must be a valid path");
        }
        return new ValidationResult();
    }

    private static @Nullable Path readFolderInput(TextField input) {
        String text = Strings.trim(input.getText());
        return Strings.isNullOrEmpty(text)
                ? null
                : Path.of(text).toAbsolutePath().normalize();
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

    private static void onFolderBrowse(TextField input, Path currentPath) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select folder");
        File directory = currentPath.toFile();
        if (directory.isDirectory()) {
            directoryChooser.setInitialDirectory(directory);
        }
        File selected = directoryChooser.showDialog(AppContext.stage);
        if (selected != null) {
            input.setText(selected.getAbsolutePath());
        }
    }
}
