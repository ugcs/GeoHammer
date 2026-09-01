package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.TraceTransform;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

public class CropByRegion extends McpTool {

    private final TraceTransform traceTransform;

    public CropByRegion(Model model, TraceTransform traceTransform) {
        super(model);
        this.traceTransform = traceTransform;
    }

    @Override
    public String getName() {
        return "crop_by_region";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Crop an open data file by a geographic region: keeps only "
                + "the points inside the polygon and removes the rest. Line numbering is updated "
                + "to keep lines continuous. Supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        ObjectNode polygon = addProperty(schema, "polygon", "array",
                "Polygon vertices as [latitude, longitude] pairs, at least 3.");
        ObjectNode vertex = polygon.putObject("items");
        vertex.put("type", "array");
        vertex.putObject("items").put("type", "number");
        schema.putArray("required").add("polygon");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        JsonNode polygonNode = args.get("polygon");
        if (polygonNode == null || !polygonNode.isArray() || polygonNode.size() < 3) {
            throw new IllegalArgumentException("polygon must be an array of at least 3 [latitude, longitude] pairs");
        }
        List<LatLon> polygon = new ArrayList<>(polygonNode.size());
        for (JsonNode vertexNode : polygonNode) {
            if (!vertexNode.isArray() || vertexNode.size() != 2
                    || !vertexNode.get(0).isNumber() || !vertexNode.get(1).isNumber()) {
                throw new IllegalArgumentException("Each polygon vertex must be a [latitude, longitude] pair");
            }
            polygon.add(new LatLon(vertexNode.get(0).asDouble(), vertexNode.get(1).asDouble()));
        }
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            int before = dataFile.numTraces();

            // project polygon to screen coordinates at a fixed zoom,
            // same as the trace cutter does
            MapField field = new MapField(model.getMapField());
            field.setZoom(28);
            List<Point2D> area = new ArrayList<>(polygon.size());
            for (LatLon vertex : polygon) {
                area.add(field.latLonToScreen(vertex));
            }

            traceTransform.cropLines(List.of(dataFile), field, area);

            int after = dataFile.numTraces();
            if (after == before) {
                return before == 0
                        ? "File has no points"
                        : "File not changed: all points are inside the region or none are";
            }
            return "Kept " + after + " points inside the region, removed " + (before - after);
        }));
    }
}
