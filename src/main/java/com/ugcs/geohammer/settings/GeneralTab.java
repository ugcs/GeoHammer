package com.ugcs.geohammer.settings;

import ch.randelshofer.fastdoubleparser.JavaDoubleParser;
import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.UserFolder;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.control.NodeWithLabel;
import com.ugcs.geohammer.view.control.NodeWithTopLabel;
import com.ugcs.geohammer.view.style.Theme;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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
public class GeneralTab implements SettingsTab {

    private static final Logger log = LoggerFactory.getLogger(GeneralTab.class);

    private final Model model;

    private final ThemeService themeService;

    private final UserFolder userFolder;

    private final ExecutorService executor;

    private final VBox content;

    private final ComboBox<Theme> themeSelector;

    private final TextField traceLookupThresholdInput;

    private final TextField userFolderInput;

    public GeneralTab(Model model, ThemeService themeService, UserFolder userFolder, ExecutorService executor) {
        this.model = Check.notNull(model);
        this.themeService = Check.notNull(themeService);
        this.userFolder = Check.notNull(userFolder);
        this.executor = Check.notNull(executor);

        themeSelector = new ComboBox<>();
        themeSelector.getItems().addAll(Theme.values());
        NodeWithLabel<ComboBox<Theme>> themeSelectorWithLabel = new NodeWithLabel<>(
                "Theme", themeSelector, false);

        traceLookupThresholdInput = new TextField();
        traceLookupThresholdInput.setPrefWidth(60);
        NodeWithLabel<TextField> thresholdWithLabel = new NodeWithLabel<>(
                "Trace lookup threshold (m)", traceLookupThresholdInput, false);

        userFolderInput = new TextField();
        HBox.setHgrow(userFolderInput, Priority.ALWAYS);

        Button browse = new Button("Browse...");
        browse.setOnAction(event -> onUserFolderBrowse());

        HBox userFolderWithBrowse = new HBox(Views.DEFAULT_SPACING, userFolderInput, browse);
        userFolderWithBrowse.setAlignment(Pos.CENTER_LEFT);

        NodeWithTopLabel<HBox> userFolderWithLabel = new NodeWithTopLabel<>(
                "User folder (templates are loaded from its templates subfolder)", userFolderWithBrowse);

        content = new VBox(SettingsView.VERTICAL_SPACING,
                new HBox(themeSelectorWithLabel, Views.createSpacer(), thresholdWithLabel),
                userFolderWithLabel);
    }

    @Override
    public Tab getTab() {
        return new Tab("General", content);
    }

    @Override
    public ValidationResult validate() {
        try {
            double threshold = JavaDoubleParser.parseDouble(traceLookupThresholdInput.getText());
            if (threshold < 0) {
                return ValidationResult.fromError(
                        traceLookupThresholdInput,
                        "Trace lookup threshold must be a non-negative number");
            }
        } catch (NumberFormatException e) {
            return ValidationResult.fromError(
                    traceLookupThresholdInput,
                    "Trace lookup threshold must be a valid number");
        }
        try {
            Path path = getUserFolderPath();
            if (path != null && !path.equals(userFolder.getPath()) && !Files.isDirectory(path)) {
                return ValidationResult.fromError(userFolderInput, "User folder does not exist");
            }
        } catch (InvalidPathException e) {
            return ValidationResult.fromError(userFolderInput, "User folder must be a valid path");
        }
        return new ValidationResult();
    }

    @Override
    public void load() {
        themeSelector.setValue(themeService.getTheme());
        traceLookupThresholdInput.setText(String.valueOf(model.getTraceLookupThreshold()));
        userFolderInput.setText(userFolder.getPath().toString());
    }

    @Override
    public void save() {
        Theme theme = themeSelector.getValue();
        themeService.setTheme(theme);
        try {
            double threshold = JavaDoubleParser.parseDouble(traceLookupThresholdInput.getText());
            model.setTraceLookupThreshold(threshold);
        } catch (NumberFormatException ignore) {
        }
        Path path = getUserFolderPath();
        executor.submit(() -> {
            try {
                userFolder.setPath(path);
            } catch (RuntimeException e) {
                log.error("Cannot change user folder to {}", path, e);
            }
        });
    }

    private @Nullable Path getUserFolderPath() {
        String text = Strings.trim(userFolderInput.getText());
        return Strings.isNullOrEmpty(text)
                ? null
                : Path.of(text).toAbsolutePath().normalize();
    }

    private void onUserFolderBrowse() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select user folder");
        File directory = userFolder.getPath().toFile();
        if (directory.isDirectory()) {
            directoryChooser.setInitialDirectory(directory);
        }
        File selected = directoryChooser.showDialog(AppContext.stage);
        if (selected != null) {
            userFolderInput.setText(selected.getAbsolutePath());
        }
    }
}
