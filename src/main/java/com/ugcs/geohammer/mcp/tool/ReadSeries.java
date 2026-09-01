package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import java.io.File;
import java.util.List;

public class ReadSeries extends McpTool {

    public ReadSeries(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "read_series";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Read values of a data series from an open data file. "
                + "Returns up to " + MAX_READ_COUNT + " values per call; "
                + "use start and count to page through the data. "
                + "To analyse a whole large file, use {{export_csv}} instead of paging through it.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "series", "string", "Series (column) name.");
        addProperty(schema, "start", "integer", "Index of the first value to read, default 0.");
        addProperty(schema, "count", "integer", "Number of values to read, default "
                + DEFAULT_READ_COUNT + ", maximum " + MAX_READ_COUNT + ".");
        schema.putArray("required").add("series");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        int start = args.path("start").asInt(0);
        int count = args.path("count").asInt(DEFAULT_READ_COUNT);
        if (start < 0 || count < 0) {
            throw new IllegalArgumentException("start and count must be non-negative");
        }
        int limit = Math.min(count, MAX_READ_COUNT);
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            getColumn(dataFile, seriesName);

            int total = geoData.size();
            int from = Math.min(start, total);
            int to = Math.min(from + limit, total);

            ObjectNode result = mapper.createObjectNode();
            File file = dataFile.getFile();
            result.put("file", file != null ? file.getName() : null);
            result.put("series", seriesName);
            result.put("total", total);
            result.put("start", from);
            result.put("count", to - from);
            ArrayNode values = result.putArray("values");
            for (int i = from; i < to; i++) {
                Object value = geoData.get(i).getValue(seriesName);
                if (value instanceof Number number) {
                    values.add(number.doubleValue());
                } else if (value instanceof String string) {
                    values.add(string);
                } else {
                    values.addNull();
                }
            }
            return toJson(result);
        }));
    }
}
