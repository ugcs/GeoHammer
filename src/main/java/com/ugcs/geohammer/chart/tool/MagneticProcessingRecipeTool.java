package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.MagneticProcessingUpdatedEvent;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingHistory;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingRecipeStore;
import com.ugcs.geohammer.service.magnetics.MagneticProcessingStep;
import com.ugcs.geohammer.view.Dialogs;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class MagneticProcessingRecipeTool extends ToolView {

    private final MagneticProcessingHistory processingHistory;

    private final MagneticProcessingRecipeStore recipeStore;

    private final ExecutorService executor;

    private final TextArea recipeText;

    private final Button exportButton;

    public MagneticProcessingRecipeTool(MagneticProcessingHistory processingHistory,
                                        MagneticProcessingRecipeStore recipeStore, ExecutorService executor) {
        this.processingHistory = processingHistory;
        this.recipeStore = recipeStore;
        this.executor = executor;

        recipeText = new TextArea();
        recipeText.setEditable(false);
        recipeText.setWrapText(true);
        recipeText.setPrefRowCount(5);

        exportButton = new Button("Export recipe");
        exportButton.setOnAction(event -> exportRecipe());

        getChildren().add(Tools.createToolContainer(
                new Label("Recipes are saved automatically beside the survey file."),
                recipeText,
                exportButton
        ));
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof CsvFile;
    }

    @Override
    public void updateView() {
        List<MagneticProcessingStep> steps = selectedFile != null ? processingHistory.getSteps(selectedFile) : List.of();
        recipeText.setText(formatSteps(steps));
        exportButton.setDisable(selectedFile == null || selectedFile.getFile() == null || steps.isEmpty());
    }

    private void exportRecipe() {
        if (selectedFile == null || selectedFile.getFile() == null) {
            return;
        }
        File source = selectedFile.getFile();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export magnetic processing recipe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON recipe (*.json)", "*.json"));
        chooser.setInitialFileName(source.getName() + ".magnetic-recipe.json");
        File parent = source.getParentFile();
        if (parent != null && parent.isDirectory()) {
            chooser.setInitialDirectory(parent);
        }
        File target = chooser.showSaveDialog(AppContext.stage);
        if (target == null) {
            return;
        }
        SgyFile file = selectedFile;
        List<MagneticProcessingStep> steps = processingHistory.getSteps(file);
        executor.submit(() -> {
            try {
                recipeStore.export(file, steps, Path.of(target.getPath()));
            } catch (IOException e) {
                Platform.runLater(() -> Dialogs.showError("Recipe export", e));
            }
        });
    }

    private static String formatSteps(List<MagneticProcessingStep> steps) {
        if (steps.isEmpty()) {
            return "No magnetic processing steps recorded.";
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < steps.size(); index++) {
            MagneticProcessingStep step = steps.get(index);
            if (index > 0) {
                text.append('\n');
            }
            text.append(index + 1).append(". ").append(step.type()).append(": ")
                    .append(step.inputSeries()).append(" -> ").append(step.outputSeries());
            if (!step.settings().isEmpty()) {
                text.append("  ").append(step.settings());
            }
        }
        return text.toString();
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }

    @EventListener
    private void onProcessingUpdated(MagneticProcessingUpdatedEvent event) {
        if (event.getFile() == selectedFile) {
            Platform.runLater(this::updateView);
        }
    }
}
