package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.PositionalObject;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class CutToLines extends McpTool {

    private final UndoModel undoModel;

    public CutToLines(Model model, UndoModel undoModel) {
        super(model);
        this.undoModel = undoModel;
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
        List<int[]> ranges = new ArrayList<>(rangesNode.size());
        for (JsonNode rangeNode : rangesNode) {
            if (!rangeNode.isArray() || rangeNode.size() != 2
                    || !rangeNode.get(0).canConvertToInt() || !rangeNode.get(1).canConvertToInt()) {
                throw new IllegalArgumentException("Each range must be a [from, to] pair of point indices");
            }
            ranges.add(new int[] {rangeNode.get(0).asInt(), rangeNode.get(1).asInt()});
        }
        for (int i = 0; i < ranges.size(); i++) {
            int[] range = ranges.get(i);
            if (range[0] < 0 || range[1] <= range[0]) {
                throw new IllegalArgumentException("Invalid range: [" + range[0] + ", " + range[1] + ")");
            }
            if (i > 0 && range[0] < ranges.get(i - 1)[1]) {
                throw new IllegalArgumentException("Ranges must be ascending and not overlap");
            }
        }
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> values = dataFile.getGeoData();
            int total = values.size();
            if (ranges.getLast()[1] > total) {
                throw new IllegalArgumentException("Range end " + ranges.getLast()[1]
                        + " is out of bounds, file has " + total + " points");
            }
            ColumnSchema schema = GeoData.getSchema(values);
            if (schema == null || schema.getHeaderBySemantic(Semantic.LINE.getName()) == null) {
                throw new IllegalArgumentException("File has no line column");
            }

            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();

            int[] newIndex = new int[total];
            Arrays.fill(newIndex, -1);
            List<GeoData> kept = new ArrayList<>();
            int lineIndex = 0;
            for (int[] range : ranges) {
                for (int i = range[0]; i < range[1]; i++) {
                    GeoData value = values.get(i);
                    value.setLine(lineIndex);
                    newIndex[i] = kept.size();
                    kept.add(value);
                }
                lineIndex++;
            }

            // reindex positional elements, drop the ones in removed regions
            Iterator<BaseObject> it = dataFile.getAuxElements().iterator();
            while (it.hasNext()) {
                if (it.next() instanceof PositionalObject positional) {
                    int oldIndex = positional.getTraceIndex();
                    int updated = oldIndex >= 0 && oldIndex < total ? newIndex[oldIndex] : -1;
                    if (updated == -1) {
                        it.remove();
                    } else {
                        positional.offset(updated - oldIndex);
                    }
                }
            }

            values.clear();
            values.addAll(kept);

            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            dataFile.setUnsaved(true);
            model.reload(dataFile);

            return "Kept " + kept.size() + " points in " + ranges.size() + " lines, removed "
                    + (total - kept.size()) + " points";
        }));
    }
}
