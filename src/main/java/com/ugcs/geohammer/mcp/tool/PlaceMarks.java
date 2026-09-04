package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.event.WhatChanged;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaceMarks extends McpTool {

    public PlaceMarks(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "place_marks";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Place marks (flags) at the given point indices of an open data file. "
                + "Marks are shown as flags on the chart and map, and are stored in the Mark column "
                + "when the file is saved. Indices where a mark already exists are skipped.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        ObjectNode placeIndices = addProperty(schema, "indices", "array",
                "Point indices to place marks at.");
        placeIndices.putObject("items").put("type", "integer");
        schema.putArray("required").add("indices");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        List<Integer> indices = readIndices(args);
        if (indices == null || indices.isEmpty()) {
            throw new IllegalArgumentException("indices must be a non-empty array of point indices");
        }
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            int numPoints = dataFile.numTraces();
            for (int index : indices) {
                if (index < 0 || index >= numPoints) {
                    throw new IllegalArgumentException("Point index out of bounds: " + index
                            + ", file has " + numPoints + " points");
                }
            }
            Set<Integer> markedIndices = new HashSet<>();
            for (BaseObject element : dataFile.getAuxElements()) {
                if (element instanceof FoundPlace flag) {
                    markedIndices.add(flag.getTraceIndex());
                }
            }
            Chart chart = model.getChart(dataFile);
            int placed = 0;
            for (int index : indices) {
                if (!markedIndices.add(index)) {
                    continue;
                }
                FoundPlace flag = new FoundPlace(new TraceKey(dataFile, index), model);
                dataFile.getAuxElements().add(flag);
                if (chart != null) {
                    chart.addFlag(flag);
                }
                placed++;
            }
            dataFile.setUnsaved(true);
            model.updateAuxElements();
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
            return "Placed " + placed + " marks, " + (indices.size() - placed) + " skipped as duplicates";
        }));
    }
}
