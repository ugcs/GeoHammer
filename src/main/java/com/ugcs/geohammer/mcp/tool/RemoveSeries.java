package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;

public class RemoveSeries extends McpTool {

    private final UndoModel undoModel;

    public RemoveSeries(Model model, UndoModel undoModel) {
        super(model);
        this.undoModel = undoModel;
    }

    @Override
    public String getName() {
        return "remove_series";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Delete a data series (column) and its values from an open "
                + "data file. Read-only series (positions, line numbers and other columns parsed "
                + "from the source file) cannot be deleted. Supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string", "Name of the series to delete.");
        schema.putArray("required").add("series");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            SensorLineChart chart = getSensorChart(dataFile);
            Column column = getColumn(dataFile, seriesName);
            if (column.isReadOnly()) {
                throw new IllegalArgumentException("Series is read-only and cannot be deleted: "
                        + seriesName);
            }
            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();
            chart.removeFileColumn(seriesName);
            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            return "Deleted series " + seriesName;
        }));
    }
}
