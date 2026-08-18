package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.service.script.PythonService;
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
import javafx.stage.FileChooser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Component
public class ScriptsTab implements SettingsTab {

    private final PythonService pythonService;

    private final VBox content;

    private final TextField pythonPathInput;

    public ScriptsTab(PythonService pythonService) {
        this.pythonService = Check.notNull(pythonService);

        pythonPathInput = new TextField();
        pythonPathInput.setEditable(false);

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

        content = new VBox(SettingsView.VERTICAL_SPACING, pythonPathWithLabel);
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

    @Override
    public Tab getTab() {
        return new  Tab("Scripts", content);
    }

    @Override
    public void load() {
        Path path = null;
        try {
            path = pythonService.getPythonPath();
        } catch (IOException ignore) {
        }
        pythonPathInput.setText(path != null ? path.toString() : Strings.empty());
    }

    @Override
    public void save() {
        String path = pythonPathInput.getText();
        pythonService.setPythonPath(path);
    }
}
