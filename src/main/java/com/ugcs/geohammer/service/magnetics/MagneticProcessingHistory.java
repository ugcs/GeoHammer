package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.MagneticProcessingUpdatedEvent;
import com.ugcs.geohammer.util.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Service
public class MagneticProcessingHistory {

    private static final Logger log = LoggerFactory.getLogger(MagneticProcessingHistory.class);

    private final Model model;

    private final MagneticProcessingRecipeStore recipeStore;

    private final ExecutorService executor;

    private final Map<SgyFile, List<MagneticProcessingStep>> steps =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public MagneticProcessingHistory(Model model, MagneticProcessingRecipeStore recipeStore, ExecutorService executor) {
        this.model = model;
        this.recipeStore = recipeStore;
        this.executor = executor;
    }

    public List<MagneticProcessingStep> getSteps(SgyFile file) {
        Check.notNull(file);

        synchronized (steps) {
            return List.copyOf(steps.getOrDefault(file, List.of()));
        }
    }

    public void addStep(SgyFile file, MagneticProcessingStep step) {
        Check.notNull(file);
        Check.notNull(step);

        synchronized (steps) {
            steps.computeIfAbsent(file, ignored -> new ArrayList<>()).add(step);
        }
        saveRecipe(file);
        model.publishEvent(new MagneticProcessingUpdatedEvent(this, file));
    }

    public void clear(SgyFile file) {
        Check.notNull(file);

        boolean removed;
        synchronized (steps) {
            removed = steps.remove(file) != null;
        }
        if (removed) {
            model.publishEvent(new MagneticProcessingUpdatedEvent(this, file));
        }
    }

    @EventListener
    private void onFileClosed(FileClosedEvent event) {
        clear(event.getFile());
    }

    @EventListener
    private void onFileOpened(FileOpenedEvent event) {
        for (File source : event.getFiles()) {
            SgyFile file = model.getFileManager().getFile(source);
            if (file != null) {
                executor.submit(() -> restoreRecipe(file));
            }
        }
    }

    private void saveRecipe(SgyFile file) {
        if (file.getFile() == null) {
            return;
        }
        executor.submit(() -> {
            try {
                recipeStore.write(file, getSteps(file));
            } catch (IOException e) {
                log.warn("Unable to save magnetic processing recipe", e);
            }
        });
    }

    private void restoreRecipe(SgyFile file) {
        try {
            List<MagneticProcessingStep> restoredSteps = recipeStore.read(file);
            if (restoredSteps.isEmpty()) {
                return;
            }
            synchronized (steps) {
                steps.put(file, new ArrayList<>(restoredSteps));
            }
            model.publishEvent(new MagneticProcessingUpdatedEvent(this, file));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Unable to read magnetic processing recipe", e);
        }
    }
}
