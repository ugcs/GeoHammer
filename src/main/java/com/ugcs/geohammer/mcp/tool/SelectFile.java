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
                + "and makes it the active file.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        SgyFile dataFile = resolveFile(fileName);
        Chart chart = model.getChart(dataFile);
        if (chart == null) {
            throw new IllegalArgumentException("File has no chart");
        }
        return text(inFxThread(() -> {
            model.selectAndScrollToChart(chart);
            File file = dataFile.getFile();
            return "Selected file " + (file != null ? file.getName() : "");
        }));
    }
}
