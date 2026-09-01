package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import javafx.geometry.Point2D;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class GetGridImage extends McpTool {

    private final GridLayer gridLayer;

    public GetGridImage(Model model, GridLayer gridLayer) {
        super(model);
        this.gridLayer = gridLayer;
    }

    @Override
    public String getName() {
        return "get_grid_image";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Render the current grid of an open data file to a PNG image "
                + "covering the full grid extent. Requires {{run_gridding}} first.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "width", "integer", "Image width in pixels, default 800, maximum 2000.");
        ObjectNode gridBounds = addProperty(schema, "bounds", "array",
                "Geographic region to render as [south, west, north, east] in degrees "
                + "(minimum latitude, minimum longitude, maximum latitude, maximum longitude); "
                + "zooms the image to that part of the grid. Default: the full grid extent.");
        gridBounds.putObject("items").put("type", "number");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int width = Math.clamp(args.path("width").asInt(800), 200, 2000);
        SgyFile dataFile = inFxThread(() -> resolveFile(fileName));
        GriddingResult result = gridLayer.getResult(dataFile);
        if (result == null) {
            throw new IllegalArgumentException("File has no grid, run run_gridding first");
        }
        // the displayable grid is built asynchronously after gridding
        for (int i = 0; i < 50 && gridLayer.getGrid(dataFile) == null; i++) {
            Thread.sleep(200);
        }
        if (gridLayer.getGrid(dataFile) == null) {
            throw new IllegalStateException("Grid is not ready yet, retry in a moment");
        }

        LatLon min = result.minLatLon();
        LatLon max = result.maxLatLon();
        JsonNode boundsNode = args.get("bounds");
        if (boundsNode != null && !boundsNode.isNull()) {
            if (!boundsNode.isArray() || boundsNode.size() != 4) {
                throw new IllegalArgumentException(
                        "bounds must be [south, west, north, east] in degrees");
            }
            double south = Math.max(boundsNode.get(0).asDouble(), min.getLatDgr());
            double west = Math.max(boundsNode.get(1).asDouble(), min.getLonDgr());
            double north = Math.min(boundsNode.get(2).asDouble(), max.getLatDgr());
            double east = Math.min(boundsNode.get(3).asDouble(), max.getLonDgr());
            if (south >= north || west >= east) {
                throw new IllegalArgumentException("bounds do not intersect the grid extent: "
                        + "latitude " + min.getLatDgr() + ".." + max.getLatDgr()
                        + ", longitude " + min.getLonDgr() + ".." + max.getLonDgr());
            }
            min = new LatLon(south, west);
            max = new LatLon(north, east);
        }
        LatLon center = new LatLon(
                0.5 * (min.getLatDgr() + max.getLatDgr()),
                0.5 * (min.getLonDgr() + max.getLonDgr()));

        MapField field = new MapField();
        field.setPathCenter(center);
        field.setPathEdgeLL(
                new LatLon(max.getLatDgr(), min.getLonDgr()),
                new LatLon(min.getLatDgr(), max.getLonDgr()));
        field.setSceneCenter(center);

        // extent aspect ratio in meters
        double widthMeters = new LatLon(center.getLatDgr(), min.getLonDgr())
                .getDistance(new LatLon(center.getLatDgr(), max.getLonDgr()));
        double heightMeters = new LatLon(min.getLatDgr(), center.getLonDgr())
                .getDistance(new LatLon(max.getLatDgr(), center.getLonDgr()));
        int height = Math.clamp((int) Math.round(width * heightMeters / Math.max(1e-6, widthMeters)),
                100, 2000);
        field.adjustZoom((int) (width * 0.95), (int) (height * 0.95));

        // crop to the actual extent at the selected zoom
        Point2D corner = field.latLonToScreen(field.getPathRightBottom());
        int margin = 10;
        int imageWidth = Math.clamp(2 * (int) Math.abs(corner.getX()) + 2 * margin, 100, 2000);
        int imageHeight = Math.clamp(2 * (int) Math.abs(corner.getY()) + 2 * margin, 100, 2000);

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.translate(imageWidth / 2, imageHeight / 2);
            gridLayer.drawOnMapField(g2, field);
        } finally {
            g2.dispose();
        }
        String caption = "Grid of series " + result.seriesName()
                + ", extent %.1f x %.1f m, cell size %.2f m".formatted(
                        widthMeters, heightMeters, result.params().cellSize());
        return image(toPng(image), caption);
    }
}
