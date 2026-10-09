package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.util.Check;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecipeReplayService {

    private final IgrfRemovalService igrfRemovalService;
    private final HeadingCorrectionService headingCorrectionService;
    private final LineLevelingService lineLevelingService;
    private final CrossoverLevelingService crossoverLevelingService;
    private final MicroLevelingService microLevelingService;
    private final RegionalRemovalService regionalRemovalService;
    private final MagneticProcessingWorkflow processingWorkflow;

    public RecipeReplayService(IgrfRemovalService igrfRemovalService, HeadingCorrectionService headingCorrectionService,
                               LineLevelingService lineLevelingService, CrossoverLevelingService crossoverLevelingService,
                               MicroLevelingService microLevelingService, RegionalRemovalService regionalRemovalService,
                               MagneticProcessingWorkflow processingWorkflow) {
        this.igrfRemovalService = igrfRemovalService;
        this.headingCorrectionService = headingCorrectionService;
        this.lineLevelingService = lineLevelingService;
        this.crossoverLevelingService = crossoverLevelingService;
        this.microLevelingService = microLevelingService;
        this.regionalRemovalService = regionalRemovalService;
        this.processingWorkflow = processingWorkflow;
    }

    public void replay(SensorLineChart chart, List<MagneticProcessingStep> steps) {
        Check.notNull(chart);
        Check.notEmpty(steps);
        for (MagneticProcessingStep step : steps) replayStep(chart, step);
    }

    private void replayStep(SensorLineChart chart, MagneticProcessingStep step) {
        String input = step.inputSeries();
        String output = step.outputSeries();
        switch (step.type()) {
            case DIURNAL_CORRECTION -> throw new IllegalArgumentException("Diurnal replay requires a base-station file");
            case IGRF_REMOVAL -> {
                Instant fallback = step.settings().containsKey("fallbackTimestamp")
                        ? Instant.parse(step.settings().get("fallbackTimestamp")) : null;
                chart.createDerivedSeries(output, input, igrfRemovalService.remove(chart.getFile().getGeoData(), input, fallback));
                processingWorkflow.recordIgrfRemoval(chart.getFile(), new IgrfRemovalOptions(input, output, fallback));
            }
            case HEADING_CORRECTION -> {
                int bins = integerSetting(step, "headingBins");
                chart.createDerivedSeries(output, input, headingCorrectionService.correct(chart.getFile().getGeoData(), input, bins).values());
                processingWorkflow.recordHeadingCorrection(chart.getFile(), new HeadingCorrectionOptions(input, output, bins));
            }
            case LINE_LEVELING -> replayLineLeveling(chart, step);
            case MICRO_LEVELING -> {
                int window = integerSetting(step, "windowSize");
                chart.createDerivedSeries(output, input, microLevelingService.level(chart.getFile().getGeoData(), input, window));
                processingWorkflow.recordMicroLeveling(chart.getFile(), new MicroLevelingOptions(input, output, window));
            }
            case REGIONAL_REMOVAL -> {
                int order = integerSetting(step, "polynomialOrder");
                chart.createDerivedSeries(output, input, regionalRemovalService.remove(chart.getFile().getGeoData(), input, order).values());
                processingWorkflow.recordRegionalRemoval(chart.getFile(), new RegionalRemovalOptions(input, output, order));
            }
        }
    }

    private void replayLineLeveling(SensorLineChart chart, MagneticProcessingStep step) {
        String method = step.settings().getOrDefault("method", "line-median");
        if (method.equals("line-median")) {
            chart.createDerivedSeries(step.outputSeries(), step.inputSeries(), lineLevelingService.level(chart.getFile().getGeoData(), step.inputSeries()));
            processingWorkflow.recordLineLeveling(chart.getFile(), new LineLevelingOptions(step.inputSeries(), step.outputSeries()));
            return;
        }
        if (method.equals("tie-line-crossover")) {
            Set<Integer> ties = tieLines(step);
            chart.createDerivedSeries(step.outputSeries(), step.inputSeries(), crossoverLevelingService.level(chart.getFile().getGeoData(), step.inputSeries(), ties).values());
            processingWorkflow.recordCrossoverLeveling(chart.getFile(), step.inputSeries(), step.outputSeries(), ties);
            return;
        }
        throw new IllegalArgumentException("Unsupported line-leveling method: " + method);
    }

    private static int integerSetting(MagneticProcessingStep step, String key) {
        try { return Integer.parseInt(step.settings().get(key)); }
        catch (RuntimeException e) { throw new IllegalArgumentException(step.type() + " is missing setting '" + key + "'"); }
    }

    private static Set<Integer> tieLines(MagneticProcessingStep step) {
        String value = step.settings().get("tieLineIds");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Crossover leveling is missing tie-line IDs");
        Set<Integer> ties = new LinkedHashSet<>();
        for (String item : value.split(",")) ties.add(Integer.parseInt(item));
        return ties;
    }
}
