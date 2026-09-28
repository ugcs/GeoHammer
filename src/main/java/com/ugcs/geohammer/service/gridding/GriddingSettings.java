package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.service.palette.PaletteType;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import com.ugcs.geohammer.util.Text;
import org.springframework.stereotype.Component;

@Component
public class GriddingSettings {

    private final Settings settings;

    public GriddingSettings(Settings settings) {
        this.settings = settings;
    }

    public GriddingParams loadParams(SgyFile file) {
        double cellSize = 0.1;
        double blankingDistance = 1.0;

        String templateName = Templates.getTemplateName(file);
        if (!Strings.isNullOrEmpty(templateName)) {
            cellSize = settings.getDoubleOrDefault("gridding_cellsize", templateName, cellSize);
            blankingDistance = settings.getDoubleOrDefault("gridding_blankingdistance", templateName, blankingDistance);
        }

        return new GriddingParams(cellSize, blankingDistance);
    }

    public GriddingFilter loadFilter(SgyFile file, String seriesName, Range defaultRange) {
        Range range = defaultRange;
        boolean analyticSignal = false;
        boolean hillShading = false;
        boolean smoothing = false;
        PaletteType paletteType = PaletteType.defaultPaletteType();
        SpectrumType spectrumType = SpectrumType.defaultSpectrumType();

        String templateName = Templates.getTemplateName(file);
        if (!Strings.isNullOrEmpty(templateName)) {
            if (!Strings.isNullOrEmpty(seriesName)) {
                Double rangeMin = settings.getDouble("gridding_range_min", templateName + "." + seriesName);
                Double rangeMax = settings.getDouble("gridding_range_max", templateName + "." + seriesName);
                if (rangeMin != null && rangeMax != null) {
                    range = new Range(rangeMin, rangeMax);
                }
            }
            analyticSignal = settings.getBooleanOrDefault("gridding_analytic_signal_enabled", templateName, analyticSignal);
            hillShading = settings.getBooleanOrDefault("gridding_hillshading_enabled", templateName, hillShading);
            smoothing = settings.getBooleanOrDefault("gridding_smoothing_enabled", templateName, smoothing);
            paletteType = PaletteType.findByName(settings.getStringOrDefault("gridding_palette", templateName, Strings.empty()));
            spectrumType = SpectrumType.findByName(settings.getStringOrDefault("gridding_spectrum", templateName, Strings.empty()));
        }

        return new GriddingFilter(
                range,
                analyticSignal,
                hillShading,
                smoothing,
                paletteType,
                spectrumType);
    }

    public void saveParams(SgyFile file, GriddingParams params) {
        if (params == null) {
            return;
        }
        String templateName = Templates.getTemplateName(file);
        if (Strings.isNullOrEmpty(templateName)) {
            return;
        }

        settings.setValue("gridding_cellsize", templateName, Text.formatNumber(params.cellSize()));
        settings.setValue("gridding_blankingdistance", templateName, Text.formatNumber(params.blankingDistance()));
    }

    public void saveFilter(SgyFile file, String seriesName, GriddingFilter filter) {
        if (filter == null) {
            return;
        }
        String templateName = Templates.getTemplateName(file);
        if (Strings.isNullOrEmpty(templateName)) {
            return;
        }

        settings.setValue("gridding_hillshading_enabled", templateName, Boolean.toString(filter.hillShading()));
        settings.setValue("gridding_smoothing_enabled", templateName, Boolean.toString(filter.smoothing()));
        settings.setValue("gridding_analytic_signal_enabled", templateName, Boolean.toString(filter.analyticSignal()));
        settings.setValue("gridding_palette", templateName, filter.paletteType().name());
        settings.setValue("gridding_spectrum", templateName, filter.spectrumType().name());
        Range range = filter.range();
        if (range != null && !Strings.isNullOrEmpty(seriesName)) {
            settings.setValue("gridding_range_min", templateName + "." + seriesName, Text.formatNumber(range.getMin()));
            settings.setValue("gridding_range_max", templateName + "." + seriesName, Text.formatNumber(range.getMax()));
        }
    }
}
