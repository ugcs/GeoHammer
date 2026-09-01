package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import java.util.ArrayList;
import java.util.List;

public class WriteSeries extends McpTool {

    private final UndoModel undoModel;

    public WriteSeries(Model model, UndoModel undoModel) {
        super(model);
        this.undoModel = undoModel;
    }

    @Override
    public String getName() {
        return "write_series";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Write values into a data series of an open data file "
                + "starting at the given index. Values must be numbers or nulls. "
                + "The change is shown in the app immediately, supports undo "
                + "and marks the file as unsaved.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string", "Series (column) name.");
        addProperty(schema, "start", "integer", "Index of the first value to write, default 0.");
        ObjectNode values = addProperty(schema, "values", "array", "Values to write.");
        values.putObject("items").putArray("type").add("number").add("null");
        schema.putArray("required").add("series").add("values");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        int start = args.path("start").asInt(0);
        JsonNode valuesNode = args.path("values");
        if (!valuesNode.isArray() || valuesNode.isEmpty()) {
            throw new IllegalArgumentException("values must be a non-empty array of numbers or nulls");
        }
        List<Object> values = parseValues(valuesNode);
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            Column column = getColumn(dataFile, seriesName);
            if (column.isReadOnly()) {
                throw new IllegalArgumentException("Series is read-only: " + seriesName);
            }
            if (start < 0 || start + values.size() > geoData.size()) {
                throw new IllegalArgumentException("Value range [" + start + ", "
                        + (start + values.size()) + ") is out of bounds, file has "
                        + geoData.size() + " points");
            }

            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();
            for (int i = 0; i < values.size(); i++) {
                geoData.get(start + i).setValue(seriesName, values.get(i));
            }
            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            dataFile.setUnsaved(true);
            model.reload(dataFile);

            return "Wrote " + values.size() + " values to series " + seriesName
                    + " starting at index " + start;
        }));
    }
}
