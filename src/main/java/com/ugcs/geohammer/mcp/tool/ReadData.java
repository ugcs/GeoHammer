package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReadData extends McpTool {

    public ReadData(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "read_data";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Read one or more data series aligned and decimated to at most "
                + "max_points buckets. Use for overview reads of large files instead of paging "
                + "{{read_series}}. Each bucket aggregates bucket_size consecutive values; bucket i "
                + "starts at point index start + i * bucket_size. "
                + "This is a decimated overview, never a full-resolution read: when the analysis needs "
                + "every point (line detection, cross-correlation, lag estimation), use {{export_csv}}.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        ObjectNode readDataSeries = addProperty(schema, "series", "array", "Series names to read.");
        readDataSeries.putObject("items").put("type", "string");
        addProperty(schema, "start", "integer", "Index of the first point, default 0.");
        addProperty(schema, "count", "integer", "Number of points to cover, default all.");
        addProperty(schema, "max_points", "integer",
                "Maximum values per series, default 1000, maximum 10000.");
        ObjectNode aggregate = addProperty(schema, "aggregate", "string",
                "Bucket aggregate: mean (default), min, max or first.");
        aggregate.putArray("enum").add("mean").add("min").add("max").add("first");
        schema.putArray("required").add("series");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        JsonNode seriesNode = args.get("series");
        if (seriesNode == null || !seriesNode.isArray() || seriesNode.isEmpty()) {
            throw new IllegalArgumentException("series must be a non-empty array of series names");
        }
        List<String> seriesNames = new ArrayList<>(seriesNode.size());
        for (JsonNode s : seriesNode) {
            if (!s.isTextual()) {
                throw new IllegalArgumentException("series must be an array of series names");
            }
            seriesNames.add(s.asText());
        }
        int start = args.path("start").asInt(0);
        int count = args.path("count").asInt(Integer.MAX_VALUE);
        int maxPoints = Math.min(args.path("max_points").asInt(1000), MAX_READ_COUNT);
        if (start < 0 || count < 0 || maxPoints < 1) {
            throw new IllegalArgumentException("start, count and max_points must be non-negative");
        }
        String aggregate = args.path("aggregate").asText("mean");
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            for (String seriesName : seriesNames) {
                getColumn(dataFile, seriesName);
            }
            int total = geoData.size();
            int from = Math.min(start, total);
            int to = Math.min(from + count, total);
            int span = to - from;
            int bucketSize = Math.max(1, (int) Math.ceil((double) span / maxPoints));
            int numBuckets = (span + bucketSize - 1) / bucketSize;

            ObjectNode result = mapper.createObjectNode();
            File file = dataFile.getFile();
            result.put("file", file != null ? file.getName() : null);
            result.put("total", total);
            result.put("start", from);
            result.put("count", span);
            result.put("bucket_size", bucketSize);
            result.put("aggregate", aggregate);
            ObjectNode values = result.putObject("values");
            for (String seriesName : seriesNames) {
                ArrayNode array = values.putArray(seriesName);
                for (int b = 0; b < numBuckets; b++) {
                    int bFrom = from + b * bucketSize;
                    int bTo = Math.min(bFrom + bucketSize, to);
                    double acc = 0;
                    int n = 0;
                    for (int i = bFrom; i < bTo; i++) {
                        if (geoData.get(i).getNumber(seriesName) instanceof Number number) {
                            double v = number.doubleValue();
                            n++;
                            switch (aggregate) {
                                case "min" -> acc = n == 1 ? v : Math.min(acc, v);
                                case "max" -> acc = n == 1 ? v : Math.max(acc, v);
                                case "first" -> acc = n == 1 ? v : acc;
                                default -> acc += v;
                            }
                        }
                    }
                    if (n == 0) {
                        array.addNull();
                    } else {
                        array.add("mean".equals(aggregate) ? acc / n : acc);
                    }
                }
            }
            return toJson(result);
        }));
    }
}
