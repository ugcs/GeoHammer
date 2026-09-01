package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.TraceTransform;

public class MergeLines extends McpTool {

    private final TraceTransform traceTransform;

    public MergeLines(Model model, TraceTransform traceTransform) {
        super(model);
        this.traceTransform = traceTransform;
    }

    @Override
    public String getName() {
        return "merge_lines";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Merge a survey line with the following line. Supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "line", "integer",
                "Line index to merge with the following line, see {{list_lines}}.");
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
            if (!traceTransform.hasNextLine(dataFile, range.from())) {
                throw new IllegalArgumentException("Line " + line + " has no following line to merge with");
            }
            traceTransform.mergeLineWithNext(dataFile, range.from());
            return "Merged line " + line + " with the following line";
        }));
    }
}
