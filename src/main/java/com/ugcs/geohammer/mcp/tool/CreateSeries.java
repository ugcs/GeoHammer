package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.util.Strings;

import java.util.ArrayList;
import java.util.List;

public class CreateSeries extends McpTool {

    private final UndoModel undoModel;

    public CreateSeries(Model model, UndoModel undoModel) {
        super(model);
        this.undoModel = undoModel;
    }

    @Override
    public String getName() {
        return "create_series";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Create a new data series (column) in an open data file, "
                + "optionally filling initial values starting from index 0. "
                + "The series is shown on the chart and supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string", "Name of the series to create.");
        addProperty(schema, "unit", "string", "Optional measurement unit of the series.");
        ObjectNode initValues = addProperty(schema, "values", "array",
                "Optional initial values, set starting from index 0.");
        initValues.putObject("items").putArray("type").add("number").add("null");
        schema.putArray("required").add("series");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        String unit = optionalString(args, "unit");
        JsonNode valuesNode = args.get("values");
        List<Object> values = valuesNode != null && valuesNode.isArray()
                ? parseValues(valuesNode)
                : List.of();
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            ColumnSchema schema = GeoData.getSchema(geoData);
            if (schema == null) {
                throw new IllegalArgumentException("File has no data");
            }
            if (schema.getColumn(seriesName) != null) {
                throw new IllegalArgumentException("Series already exists: " + seriesName);
            }
            if (values.size() > geoData.size()) {
                throw new IllegalArgumentException("Too many values: " + values.size()
                        + ", file has " + geoData.size() + " points");
            }

            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();
            Column column = GeoData.addColumn(geoData, new Column(seriesName));
            column.setDisplay(true);
            if (!Strings.isNullOrEmpty(unit)) {
                column.setUnit(unit);
            }
            for (int i = 0; i < values.size(); i++) {
                geoData.get(i).setValue(seriesName, values.get(i));
            }
            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            dataFile.setUnsaved(true);
            model.reload(dataFile);

            return "Created series " + seriesName
                    + (values.isEmpty() ? "" : " with " + values.size() + " initial values");
        }));
    }
}
