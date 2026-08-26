package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.TraceTransform;

public class SplitLine extends McpTool {

    private final TraceTransform traceTransform;

    public SplitLine(Model model, TraceTransform traceTransform) {
        super(model);
        this.traceTransform = traceTransform;
    }

    @Override
    public String getName() {
        return "split_line";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Split (cut) a survey line into two at the given point index; "
                + "the point becomes the start of a new line. Supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "index", "integer", "Point index where the line is cut.");
        schema.putArray("required").add("index");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int index = args.path("index").asInt(-1);
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            if (index < 0 || index >= dataFile.numTraces()) {
                throw new IllegalArgumentException("Point index out of bounds: " + index
                        + ", file has " + dataFile.numTraces() + " points");
            }
            if (traceTransform.isStartOfLine(dataFile, index)) {
                throw new IllegalArgumentException("Point " + index + " is already a start of a line");
            }
            traceTransform.splitLine(dataFile, index);
            return "Split line at point " + index;
        }));
    }
}
