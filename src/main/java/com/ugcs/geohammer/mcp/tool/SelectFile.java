package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import java.io.File;

public class SelectFile extends McpTool {

    public SelectFile(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "select_file";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Select a file in the GeoHammer UI: scrolls its chart into view "
                + "and makes it the active file. The active file is the default target of all tools "
                + "when their file argument is omitted.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        schema.putArray("required").add("file");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = requiredString(args, "file");
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            Chart chart = model.getChart(dataFile);
            if (chart == null) {
                throw new IllegalArgumentException("File has no chart");
            }
            model.selectAndScrollToChart(chart);
            File file = dataFile.getFile();
            return "Selected file " + (file != null ? file.getName() : "");
        }));
    }
}
