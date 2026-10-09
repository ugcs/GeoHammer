package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.util.Check;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class MagneticProcessingWorkflow {

    private final MagneticProcessingHistory history;

    public MagneticProcessingWorkflow(MagneticProcessingHistory history) {
        this.history = history;
    }

    public void recordDiurnalCorrection(SgyFile file, DiurnalCorrectionOptions options) {
        Check.notNull(options);
        Map<String, String> settings = options.referenceField() != null
                ? Map.of(
                        "baseStationSeries", options.baseStationSeries(),
                        "referenceField", Double.toString(options.referenceField())
                )
                : Map.of("baseStationSeries", options.baseStationSeries());
        record(file, MagneticProcessingStepType.DIURNAL_CORRECTION,
                options.inputSeries(), options.outputSeries(), settings);
    }

    public void recordIgrfRemoval(SgyFile file, IgrfRemovalOptions options) {
        Check.notNull(options);

        Map<String, String> settings = options.fallbackTimestamp() != null
                ? Map.of("fallbackTimestamp", options.fallbackTimestamp().toString())
                : Map.of();
        record(file, MagneticProcessingStepType.IGRF_REMOVAL,
                options.inputSeries(), options.outputSeries(), settings);
    }

    public void recordLineLeveling(SgyFile file, LineLevelingOptions options) {
        Check.notNull(options);
        record(file, MagneticProcessingStepType.LINE_LEVELING,
                options.inputSeries(), options.outputSeries(), Map.of("method", "line-median"));
    }

    public void recordMicroLeveling(SgyFile file, MicroLevelingOptions options) {
        Check.notNull(options);
        record(file, MagneticProcessingStepType.MICRO_LEVELING,
                options.inputSeries(), options.outputSeries(),
                Map.of("method", "neighbor-residual-median", "windowSize", Integer.toString(options.windowSize())));
    }

    public void recordRegionalRemoval(SgyFile file, RegionalRemovalOptions options) {
        Check.notNull(options);
        record(file, MagneticProcessingStepType.REGIONAL_REMOVAL,
                options.inputSeries(), options.outputSeries(),
                Map.of("polynomialOrder", Integer.toString(options.polynomialOrder())));
    }

    private void record(SgyFile file, MagneticProcessingStepType type, String inputSeries,
                        String outputSeries, Map<String, String> settings) {
        Check.notNull(file);
        history.addStep(file, new MagneticProcessingStep(type, inputSeries, outputSeries, settings, Instant.now()));
    }
}
