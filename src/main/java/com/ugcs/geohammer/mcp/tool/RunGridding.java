package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.mcp.McpSession;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingParams;
import com.ugcs.geohammer.service.gridding.GriddingSettings;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.service.palette.PaletteType;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class RunGridding extends McpTool {

    private final GriddingService griddingService;

    private final GridLayer gridLayer;

    private final GriddingSettings griddingSettings;

    public RunGridding(Model model, GriddingService griddingService, GridLayer gridLayer,
                       GriddingSettings griddingSettings) {
        super(model);
        this.griddingService = griddingService;
        this.gridLayer = gridLayer;
        this.griddingSettings = griddingSettings;
    }

    @Override
    public String getName() {
        return "run_gridding";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Run gridding (spatial interpolation) for a data series of "
                + "an open data file, or change display options of its current grid. The grid is shown "
                + "as a map overlay. Passing cell_size or blanking_distance runs the gridding, which may "
                + "take a while on large files; omit both to only change the display options. "
                + "Display options match the gridding tool in the UI; omitted options keep their "
                + "current values for the series.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string",
                "Series name; defaults to the series of the current grid when only display options "
                + "are changed, otherwise to the series selected in the UI.");
        addProperty(schema, "cell_size", "number",
                "Grid cell size in meters. Required if the file has no grid, otherwise defaults "
                + "to the value of the current grid.");
        addProperty(schema, "blanking_distance", "number",
                "Blanking distance in meters: cells farther than this from any data point are left empty. "
                + "Required if the file has no grid, otherwise defaults to the value of the current grid.");
        addProperty(schema, "range_min", "number",
                "Lower bound of the color scale in series units, lower values get the first color.");
        addProperty(schema, "range_max", "number",
                "Upper bound of the color scale in series units, higher values get the last color.");
        addProperty(schema, "analytic_signal", "boolean",
                "Show the analytic signal magnitude instead of the series values; the color scale "
                + "then spans the signal values and range_min and range_max are not applied.");
        addProperty(schema, "smoothing", "boolean", "Apply Gaussian smoothing to the grid.");
        addProperty(schema, "hill_shading", "boolean", "Apply hill-shading to the grid colors.");
        ObjectNode palette = addProperty(schema, "palette", "string",
                "Distribution of colors over the range: linear (uniform), gaussian (by the normal "
                + "distribution of the values) or histogram (by value quantiles).");
        palette.set("enum", enumNames(PaletteType.values()));
        ObjectNode spectrum = addProperty(schema, "spectrum", "string", "Color spectrum.");
        spectrum.set("enum", enumNames(SpectrumType.values()));
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(McpSession session, JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesArg = optionalString(args, "series");
        Double rangeMin = optionalDouble(args, "range_min");
        Double rangeMax = optionalDouble(args, "range_max");
        String paletteArg = optionalString(args, "palette");
        String spectrumArg = optionalString(args, "spectrum");

        SgyFile dataFile = resolveFile(fileName);
        GriddingResult currentResult = gridLayer.getResult(dataFile);
        // null when only display options are changed
        GriddingParams params = getParams(args, currentResult);
        String series;
        if (!Strings.isNullOrEmpty(seriesArg)) {
            series = seriesArg;
        } else if (params == null && currentResult != null) {
            series = currentResult.seriesName();
        } else {
            series = model.getSelectedSeriesName(dataFile);
        }
        if (Strings.isNullOrEmpty(series)) {
            throw new IllegalArgumentException("No series selected, specify a series name");
        }
        getColumn(dataFile, series);
        if (params == null && (currentResult == null || !series.equals(currentResult.seriesName()))) {
            throw new IllegalArgumentException("File has no grid of series " + series
                    + ", specify cell_size and blanking_distance to run gridding");
        }
        GridTarget target = new GridTarget(dataFile, series);

        // start from the current display filter of the series, so that
        // omitted options are kept as set in the gridding tool
        GriddingFilter current = gridLayer.getFilter(target.file(), target.series());
        if (current == null) {
            current = griddingSettings.loadFilter(target.file(), target.series(),
                    getSeriesRange(target.file(), target.series()));
        }
        Range range = current.range();
        if (rangeMin != null || rangeMax != null) {
            range = new Range(
                    rangeMin != null ? rangeMin : range.getMin(),
                    rangeMax != null ? rangeMax : range.getMax());
            if (range.getMin() >= range.getMax()) {
                throw new IllegalArgumentException("range_min must be less than range_max, "
                        + "current range is " + formatRange(current.range()));
            }
        }
        GriddingFilter filter = new GriddingFilter(
                range,
                optionalBoolean(args, "analytic_signal", current.analyticSignal()),
                current.reductionToPole(),
                current.rtpInclination(),
                current.rtpDeclination(),
                optionalBoolean(args, "hill_shading", current.hillShading()),
                optionalBoolean(args, "smoothing", current.smoothing()),
                paletteArg != null
                        ? parseEnum(PaletteType.class, paletteArg, "palette")
                        : current.paletteType(),
                spectrumArg != null
                        ? parseEnum(SpectrumType.class, spectrumArg, "spectrum")
                        : current.spectrumType());
        gridLayer.setFilter(target.file(), target.series(), filter);

        String message = "Display options updated for series " + target.series();
        if (params != null) {
            // gridding runs on a background thread, as the gridding tool does
            GriddingResult result = griddingService.runGridding(
                    List.of(target.file()), target.series(), params);
            if (result == null) {
                throw new IllegalArgumentException("Gridding produced no grid: the series has no values "
                        + "or the data extent is smaller than the cell size");
            }
            gridLayer.setResult(target.file(), result);

            float[][] grid = result.grid();
            int width = grid.length;
            int height = width > 0 ? grid[0].length : 0;
            message = "Gridding completed for series " + target.series()
                    + ", grid size " + width + " x " + height + " cells";
        }
        return text(message
                + "; display: range " + (filter.analyticSignal() ? "auto" : formatRange(filter.range()))
                + ", palette " + enumName(filter.paletteType())
                + ", spectrum " + enumName(filter.spectrumType())
                + ", analytic signal " + onOff(filter.analyticSignal())
                + ", smoothing " + onOff(filter.smoothing())
                + ", hill-shading " + onOff(filter.hillShading()));
    }

    private record GridTarget(SgyFile file, String series) {
    }

    private static Range getSeriesRange(SgyFile dataFile, String seriesName) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (GeoData value : dataFile.getGeoData()) {
            Number number = value.getNumber(seriesName);
            if (number != null) {
                min = Math.min(min, number.doubleValue());
                max = Math.max(max, number.doubleValue());
            }
        }
        if (min > max) {
            throw new IllegalArgumentException("Series has no numeric values: " + seriesName);
        }
        Range range = new Range(min, max);
        // widen the range of a constant series, as the gridding tool does
        return range.getWidth() > 0 ? range : range.scaleToWidth(1, 0.5);
    }

    // missing value is taken from the current grid; null if both are omitted
    @Nullable
    private static GriddingParams getParams(JsonNode args, @Nullable GriddingResult currentResult) {
        Double cellSize = optionalDouble(args, "cell_size");
        Double blankingDistance = optionalDouble(args, "blanking_distance");
        if (cellSize == null && blankingDistance == null) {
            return null;
        }
        if (currentResult != null) {
            GriddingParams currentParams = currentResult.params();
            cellSize = cellSize != null ? cellSize : currentParams.cellSize();
            blankingDistance = blankingDistance != null ? blankingDistance : currentParams.blankingDistance();
        }
        if (cellSize == null || blankingDistance == null) {
            throw new IllegalArgumentException("File has no grid, specify both cell_size and blanking_distance");
        }
        if (cellSize <= 0 || blankingDistance <= 0) {
            throw new IllegalArgumentException("cell_size and blanking_distance must be positive");
        }
        return new GriddingParams(cellSize, blankingDistance);
    }

    @Nullable
    private static Double optionalDouble(JsonNode args, String name) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        return node.asDouble();
    }

    private static boolean optionalBoolean(JsonNode args, String name, boolean defaultValue) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isBoolean()) {
            throw new IllegalArgumentException(name + " must be true or false");
        }
        return node.asBoolean();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        for (E constant : type.getEnumConstants()) {
            if (enumName(constant).equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + name + ": " + value
                + "; expected one of " + enumNames(type.getEnumConstants()));
    }

    private static ArrayNode enumNames(Enum<?>[] constants) {
        ArrayNode names = mapper.createArrayNode();
        for (Enum<?> constant : constants) {
            names.add(enumName(constant));
        }
        return names;
    }

    private static String enumName(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT);
    }

    private static String formatRange(Range range) {
        return Text.formatNumber(range.getMin()) + ".." + Text.formatNumber(range.getMax());
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
