package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import java.io.File;

public class GetChartImage extends McpTool {

    public GetChartImage(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "get_chart_image";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Screenshot of the chart of an open data file "
                + "as shown in the GeoHammer UI.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            Chart chart = model.getChart(dataFile);
            if (chart == null) {
                throw new IllegalArgumentException("File has no chart");
            }
            File file = dataFile.getFile();
            String caption = "Chart of " + (file != null ? file.getName() : "file");
            return image(snapshotNode(chart.getRootNode()), caption);
        });
    }
}
