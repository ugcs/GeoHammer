package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.util.Check;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecipeValidationService {

    public RecipeValidationResult validate(SgyFile file, List<MagneticProcessingStep> steps) {
        Check.notNull(file);
        Check.notNull(steps);

        Set<String> availableSeries = new LinkedHashSet<>();
        for (GeoData value : file.getGeoData()) {
            for (com.ugcs.geohammer.model.Column column : value.getSchema()) {
                availableSeries.add(column.getHeader());
            }
            break;
        }
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) {
            MagneticProcessingStep step = steps.get(index);
            String prefix = "Step " + (index + 1) + " (" + step.type() + "): ";
            if (!availableSeries.contains(step.inputSeries())) {
                errors.add(prefix + "input series '" + step.inputSeries() + "' is not available");
            }
            if (availableSeries.contains(step.outputSeries())) {
                errors.add(prefix + "output series '" + step.outputSeries() + "' already exists");
            }
            availableSeries.add(step.outputSeries());
        }
        return new RecipeValidationResult(List.copyOf(steps), List.copyOf(errors));
    }
}
