package com.ugcs.geohammer.service.magnetics;

import com.google.gson.annotations.Expose;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.GsonConfig;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MagneticProcessingRecipeStore {

    private static final int FORMAT_VERSION = 1;

    public void write(SgyFile file, List<MagneticProcessingStep> steps) throws IOException {
        Check.notNull(file);
        Check.notNull(steps);

        write(getRecipePath(file), file, steps);
    }

    public void export(SgyFile file, List<MagneticProcessingStep> steps, Path path) throws IOException {
        Check.notNull(file);
        Check.notNull(steps);
        Check.notNull(path);

        write(path, file, steps);
    }

    public List<MagneticProcessingStep> read(SgyFile file) throws IOException {
        Check.notNull(file);

        Path path = getRecipePath(file);
        if (!Files.exists(path)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            RecipeDocument document = GsonConfig.GSON.fromJson(reader, RecipeDocument.class);
            return document != null ? document.toSteps() : List.of();
        }
    }

    public Path getRecipePath(SgyFile file) {
        Check.notNull(file);
        java.io.File source = file.getFile();
        Check.condition(source != null, "Processing recipe requires a saved survey file");
        return Path.of(source.getPath() + ".magnetic-recipe.json");
    }

    private static void write(Path path, SgyFile file, List<MagneticProcessingStep> steps) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GsonConfig.GSON.toJson(RecipeDocument.of(file, steps), writer);
        }
    }

    private static class RecipeDocument {

        @Expose
        private int formatVersion;

        @Expose
        private String sourceFile;

        @Expose
        private List<RecipeStep> steps;

        private static RecipeDocument of(SgyFile file, List<MagneticProcessingStep> steps) {
            RecipeDocument document = new RecipeDocument();
            document.formatVersion = FORMAT_VERSION;
            java.io.File source = file.getFile();
            document.sourceFile = source != null ? source.getName() : "Unsaved survey";
            document.steps = new ArrayList<>(steps.size());
            for (MagneticProcessingStep step : steps) {
                document.steps.add(RecipeStep.of(step));
            }
            return document;
        }

        private List<MagneticProcessingStep> toSteps() {
            if (formatVersion != FORMAT_VERSION || steps == null) {
                return List.of();
            }
            List<MagneticProcessingStep> processingSteps = new ArrayList<>(steps.size());
            for (RecipeStep step : steps) {
                MagneticProcessingStep processingStep = step.toStep();
                if (processingStep != null) {
                    processingSteps.add(processingStep);
                }
            }
            return processingSteps;
        }
    }

    private static class RecipeStep {

        @Expose
        private String type;

        @Expose
        private String inputSeries;

        @Expose
        private String outputSeries;

        @Expose
        private Map<String, String> settings;

        @Expose
        private String createdAt;

        private static RecipeStep of(MagneticProcessingStep step) {
            RecipeStep recipeStep = new RecipeStep();
            recipeStep.type = step.type().name();
            recipeStep.inputSeries = step.inputSeries();
            recipeStep.outputSeries = step.outputSeries();
            recipeStep.settings = step.settings();
            recipeStep.createdAt = step.createdAt().toString();
            return recipeStep;
        }

        private @Nullable MagneticProcessingStep toStep() {
            try {
                return new MagneticProcessingStep(MagneticProcessingStepType.valueOf(type), inputSeries, outputSeries,
                        settings != null ? settings : Map.of(), Instant.parse(createdAt));
            } catch (RuntimeException e) {
                return null;
            }
        }
    }
}
