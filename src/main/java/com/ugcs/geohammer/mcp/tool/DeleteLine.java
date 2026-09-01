package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.TraceTransform;

public class DeleteLine extends McpTool {

    private final TraceTransform traceTransform;

    public DeleteLine(Model model, TraceTransform traceTransform) {
        super(model);
        this.traceTransform = traceTransform;
    }

    @Override
    public String getName() {
        return "delete_line";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Delete a survey line and all its points. Supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "line", "integer", "Line index to delete, see {{list_lines}}.");
        schema.putArray("required").add("line");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int line = args.path("line").asInt(-1);
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            IndexRange range = getLineRange(dataFile, line);
            int points = range.to() - range.from();
            traceTransform.removeLine(dataFile, line);
            return "Deleted line " + line + " with " + points + " points";
        }));
    }
}
