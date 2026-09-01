package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;

public class SelectSeries extends McpTool {

    public SelectSeries(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "select_series";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Select a data series in the GeoHammer UI: scrolls to the file "
                + "chart and highlights the series. The selected series is the default target "
                + "for filters and gridding.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string", "Series (column) name.");
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
            if (!chart.getSeriesNames().contains(seriesName)) {
                throw new IllegalArgumentException("Series has no chart: " + seriesName
                        + "; series with charts: " + String.join(", ", chart.getSeriesNames()));
            }
            model.selectAndScrollToChart(chart);
            chart.selectChart(seriesName);
            return "Selected series " + seriesName;
        }));
    }
}
