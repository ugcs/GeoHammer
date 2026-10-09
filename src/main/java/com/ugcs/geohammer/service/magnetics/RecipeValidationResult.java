package com.ugcs.geohammer.service.magnetics;

import java.util.List;

public record RecipeValidationResult(List<MagneticProcessingStep> steps, List<String> errors) {

    public boolean isValid() {
        return errors.isEmpty();
    }
}
