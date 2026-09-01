package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Templates;
import java.io.File;
import java.time.Instant;
import java.util.List;

public class GetFileInfo extends McpTool {

    public GetFileInfo(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "get_file_info";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Summary of an open data file: points, lines, series, time range, "
                + "sample rate and position update rate.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            ObjectNode result = mapper.createObjectNode();
            File file = dataFile.getFile();
            result.put("name", file != null ? file.getName() : null);
            result.put("path", file != null ? file.getAbsolutePath() : null);
            result.put("type", fileType(dataFile));
            result.put("template", Templates.getTemplateName(dataFile));
            result.put("points", geoData.size());
            result.put("unsaved", dataFile.isUnsaved());
            result.put("lines", dataFile.getLineRanges().size());
            ColumnSchema schema = GeoData.getSchema(geoData);
            result.put("series", schema != null ? schema.numColumns() : 0);

            Long first = null;
            Long last = null;
            int positionUpdates = 0;
            Double prevLat = null;
            Double prevLon = null;
            for (GeoData value : geoData) {
                Long timestamp = value.getTimestamp();
                if (timestamp != null) {
                    if (first == null) {
                        first = timestamp;
                    }
                    last = timestamp;
                }
                Double lat = value.getLatitude();
                Double lon = value.getLongitude();
                if (lat != null && lon != null
                        && (!lat.equals(prevLat) || !lon.equals(prevLon))) {
                    positionUpdates++;
                    prevLat = lat;
                    prevLon = lon;
                }
            }
            if (first != null && last > first) {
                double duration = (last - first) / 1000.0;
                result.put("startTime", Instant.ofEpochMilli(first).toString());
                result.put("endTime", Instant.ofEpochMilli(last).toString());
                result.put("durationSeconds", Math.round(duration * 1000.0) / 1000.0);
                result.put("sampleRateHz", Math.round((geoData.size() - 1) / duration * 10.0) / 10.0);
                if (positionUpdates > 1) {
                    result.put("positionUpdateRateHz",
                            Math.round((positionUpdates - 1) / duration * 10.0) / 10.0);
                    result.put("samplesPerPositionUpdate",
                            Math.round((double) geoData.size() / positionUpdates));
                }
            }
            return toJson(result);
        }));
    }
}
