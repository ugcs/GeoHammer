package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.service.TraceTransform;

import java.util.ArrayList;
import java.util.List;

public class CutToLines extends McpTool {

    private final TraceTransform traceTransform;

    public CutToLines(Model model, TraceTransform traceTransform) {
        super(model);
        this.traceTransform = traceTransform;
    }

    @Override
    public String getName() {
        return "cut_to_lines";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Keep only the given point index ranges of an open data file; "
                + "each range becomes a survey line, all other points (turns, warm-up) are removed. "
                + "Single atomic operation with one undo step. Ranges are [from, to) pairs "
                + "in ascending order without overlaps.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        ObjectNode ranges = addProperty(schema, "ranges", "array",
                "Point index ranges to keep as lines: [[from, to], ...], to is exclusive.");
        ObjectNode rangeItem = ranges.putObject("items");
        rangeItem.put("type", "array");
        rangeItem.putObject("items").put("type", "integer");
        schema.putArray("required").add("ranges");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        JsonNode rangesNode = args.get("ranges");
        if (rangesNode == null || !rangesNode.isArray() || rangesNode.isEmpty()) {
            throw new IllegalArgumentException("ranges must be a non-empty array of [from, to] pairs");
        }
        List<IndexRange> ranges = new ArrayList<>(rangesNode.size());
        for (JsonNode rangeNode : rangesNode) {
            if (!rangeNode.isArray() || rangeNode.size() != 2
                    || !rangeNode.get(0).canConvertToInt() || !rangeNode.get(1).canConvertToInt()) {
                throw new IllegalArgumentException("Each range must be a [from, to] pair of point indices");
            }
            int from = rangeNode.get(0).asInt();
            int to = rangeNode.get(1).asInt();
            if (from < 0 || to <= from) {
                throw new IllegalArgumentException("Invalid range: [" + from + ", " + to + ")");
            }
            if (!ranges.isEmpty() && from < ranges.getLast().to()) {
                throw new IllegalArgumentException("Ranges must be ascending and not overlap");
            }
            ranges.add(new IndexRange(from, to));
        }
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> values = dataFile.getGeoData();
            int total = values.size();
            if (ranges.getLast().to() > total) {
                throw new IllegalArgumentException("Range end " + ranges.getLast().to()
                        + " is out of bounds, file has " + total + " points");
            }
            ColumnSchema schema = GeoData.getSchema(values);
            if (schema == null || schema.getHeaderBySemantic(Semantic.LINE.getName()) == null) {
                throw new IllegalArgumentException("File has no line column");
            }

            int kept = 0;
            for (IndexRange range : ranges) {
                kept += range.size();
            }
            traceTransform.cropLinesToRanges(dataFile, ranges);

            return "Kept " + kept + " points in " + ranges.size() + " lines, removed "
                    + (total - kept) + " points";
        }));
    }
}
