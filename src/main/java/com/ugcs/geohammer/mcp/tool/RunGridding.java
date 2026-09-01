package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingParams;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.service.palette.PaletteType;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Strings;
import java.util.List;

public class RunGridding extends McpTool {

    private final GriddingService griddingService;

    private final GridLayer gridLayer;

    public RunGridding(Model model, GriddingService griddingService, GridLayer gridLayer) {
        super(model);
        this.griddingService = griddingService;
        this.gridLayer = gridLayer;
    }

    @Override
    public String getName() {
        return "run_gridding";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Run gridding (spatial interpolation) for a data series of "
                + "an open data file. The resulting grid is shown as a map overlay. "
                + "May take a while on large files.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string",
                "Series name; defaults to the series selected in the UI.");
        addProperty(schema, "cell_size", "number", "Grid cell size in meters.");
        addProperty(schema, "blanking_distance", "number",
                "Blanking distance in meters: cells farther than this from any data point are left empty.");
        schema.putArray("required").add("cell_size").add("blanking_distance");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesArg = optionalString(args, "series");
        double cellSize = args.path("cell_size").asDouble(0);
        double blankingDistance = args.path("blanking_distance").asDouble(0);
        if (cellSize <= 0 || blankingDistance <= 0) {
            throw new IllegalArgumentException("cell_size and blanking_distance must be positive");
        }

        GridTarget target = inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            String series = !Strings.isNullOrEmpty(seriesArg)
                    ? seriesArg
                    : model.getSelectedSeriesName(dataFile);
            if (Strings.isNullOrEmpty(series)) {
                throw new IllegalArgumentException("No series selected, specify a series name");
            }
            getColumn(dataFile, series);
            return new GridTarget(dataFile, series);
        });

        // set a default display filter so that the grid gets rendered
        if (gridLayer.getFilter(target.file(), target.series()) == null) {
            Range range = getSeriesRange(target.file(), target.series());
            gridLayer.setFilter(target.file(), target.series(), new GriddingFilter(
                    range, false, false, false,
                    PaletteType.defaultPaletteType(), SpectrumType.defaultSpectrumType()));
        }

        // gridding runs on a background thread, as the gridding tool does
        GriddingParams params = new GriddingParams(cellSize, blankingDistance);
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
        return text("Gridding completed for series " + target.series()
                + ", grid size " + width + " x " + height + " cells");
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
        return new Range(min, max);
    }
}
