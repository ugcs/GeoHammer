package com.ugcs.geohammer.service.magnetics;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IgrfModelTest {

    private final IgrfModel model = new IgrfModel();

    @Test
    void evaluate_returnsPlausibleMainFieldIntensity() {
        IgrfField field = model.evaluate(Instant.parse("2025-01-01T00:00:00Z"), 40.015, -105.27, 1600);

        assertTrue(field.totalIntensity() > 20_000 && field.totalIntensity() < 70_000, field.toString());
        assertTrue(field.down() > 0, field.toString());
    }

    @Test
    void evaluate_rejectsDatesOutsideIgrf14Range() {
        assertThrows(IllegalArgumentException.class,
                () -> model.evaluate(Instant.parse("2031-01-01T00:00:00Z"), 0, 0, 0));
    }
}
