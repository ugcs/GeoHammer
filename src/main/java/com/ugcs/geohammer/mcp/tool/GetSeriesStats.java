package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Strings;
import java.util.ArrayList;
import java.util.List;

public class GetSeriesStats extends McpTool {

    public GetSeriesStats(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "get_series_stats";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Statistics of a data series: count, nulls, min, max, mean, "
                + "standard deviation and percentiles. Scope to a point range or a single line "
                + "with the optional arguments.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string", "Series (column) name.");
        addProperty(schema, "start", "integer", "Index of the first point, default 0.");
        addProperty(schema, "count", "integer", "Number of points, default all.");
        addProperty(schema, "line", "integer",
                "Line index to scope the stats to, overrides start and count.");
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
            Column column = getColumn(dataFile, seriesName);
            List<GeoData> geoData = dataFile.getGeoData();
            int from;
            int to;
            if (args.has("line")) {
                IndexRange range = getLineRange(dataFile, args.path("line").asInt(-1));
                from = range.from();
                to = range.to();
            } else {
                from = Math.max(0, args.path("start").asInt(0));
                from = Math.min(from, geoData.size());
                to = Math.min(from + args.path("count").asInt(Integer.MAX_VALUE), geoData.size());
            }
            List<Double> values = new ArrayList<>(to - from);
            double sum = 0;
            for (int i = from; i < to; i++) {
                Number number = geoData.get(i).getNumber(seriesName);
                if (number != null) {
                    double v = number.doubleValue();
                    values.add(v);
                    sum += v;
                }
            }
            ObjectNode result = mapper.createObjectNode();
            result.put("series", seriesName);
            if (!Strings.isNullOrEmpty(column.getUnit())) {
                result.put("unit", column.getUnit());
            }
            String description = seriesDescription(dataMapping(dataFile), seriesName);
            if (description != null) {
                result.put("description", description);
            }
            result.put("start", from);
            result.put("count", to - from);
            result.put("nulls", to - from - values.size());
            if (values.isEmpty()) {
                return toJson(result);
            }
            values.sort(null);
            int n = values.size();
            double mean = sum / n;
            double variance = 0;
            for (double v : values) {
                variance += (v - mean) * (v - mean);
            }
            result.put("min", values.getFirst());
            result.put("max", values.getLast());
            result.put("mean", mean);
            result.put("std", Math.sqrt(variance / n));
            result.put("median", values.get(n / 2));
            ObjectNode percentiles = result.putObject("percentiles");
            for (int p : new int[] {1, 5, 25, 75, 95, 99}) {
                percentiles.put("p" + p, values.get(Math.min(n - 1, (int) (p / 100.0 * n))));
            }
            return toJson(result);
        }));
    }
}
