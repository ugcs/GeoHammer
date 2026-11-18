package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.template.UserTemplatesFolder;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.control.NodeWithTopLabel;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.controlsfx.validation.ValidationResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

@Component
public class TemplatesTab implements SettingsTab {

    private static final Logger log = LoggerFactory.getLogger(TemplatesTab.class);

    private final FileTemplates fileTemplates;

    private final ExecutorService executor;

    private final VBox content;

    private final TextField userTemplatesFolderInput;

    public TemplatesTab(FileTemplates fileTemplates, ExecutorService executor) {
        this.fileTemplates = Check.notNull(fileTemplates);
        this.executor = Check.notNull(executor);

        userTemplatesFolderInput = new TextField();
        userTemplatesFolderInput.setPromptText(UserTemplatesFolder.getDefaultPath().toString());
        HBox.setHgrow(userTemplatesFolderInput, Priority.ALWAYS);

        Button browse = new Button("Browse...");
        browse.setOnAction(event -> onUserTemplatesFolderBrowse());

        HBox userTemplatesFolderWithBrowse = new HBox(Views.DEFAULT_SPACING, userTemplatesFolderInput, browse);
        userTemplatesFolderWithBrowse.setAlignment(Pos.CENTER_LEFT);

        NodeWithTopLabel<HBox> userTemplatesFolderWithLabel = new NodeWithTopLabel<>(
                "User templates folder", userTemplatesFolderWithBrowse);

        content = new VBox(SettingsView.VERTICAL_SPACING, userTemplatesFolderWithLabel);
    }

    @Override
    public Tab getTab() {
        return new Tab("Templates", content);
    }

    @Override
    public ValidationResult validate() {
        try {
            Path path = readUserTemplatesFolderInput();
            if (path != null && !path.equals(fileTemplates.getUserTemplatesPath()) && !Files.isDirectory(path)) {
                return ValidationResult.fromError(userTemplatesFolderInput, "User templates folder does not exist");
            }
        } catch (InvalidPathException e) {
            return ValidationResult.fromError(userTemplatesFolderInput, "User templates folder must be a valid path");
        }
        return new ValidationResult();
    }

    @Override
    public void load() {
        userTemplatesFolderInput.setText(fileTemplates.getUserTemplatesPath().toString());
    }

    @Override
    public void save() {
        Path path = readUserTemplatesFolderInput();
        executor.submit(() -> {
            try {
                fileTemplates.setUserTemplatesPath(path);
            } catch (RuntimeException e) {
                log.error("Cannot change templates folder to {}", path, e);
            }
        });
    }

    private @Nullable Path readUserTemplatesFolderInput() {
        String text = Strings.trim(userTemplatesFolderInput.getText());
        return Strings.isNullOrEmpty(text)
                ? null
                : Path.of(text).toAbsolutePath().normalize();
    }

    private void onUserTemplatesFolderBrowse() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select templates folder");
        File directory = fileTemplates.getUserTemplatesPath().toFile();
        if (directory.isDirectory()) {
            directoryChooser.setInitialDirectory(directory);
        }
        File selected = directoryChooser.showDialog(AppContext.stage);
        if (selected != null) {
            userTemplatesFolderInput.setText(selected.getAbsolutePath());
        }
    }
}
