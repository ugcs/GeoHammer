package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.MagneticProcessingUpdatedEvent;
import com.ugcs.geohammer.util.Check;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MagneticProcessingHistory {

    private final Model model;

    private final Map<SgyFile, List<MagneticProcessingStep>> steps =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public MagneticProcessingHistory(Model model) {
        this.model = model;
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
}
