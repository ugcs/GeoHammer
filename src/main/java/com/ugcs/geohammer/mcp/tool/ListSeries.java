package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.util.Strings;

public class ListSeries extends McpTool {

    public ListSeries(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "list_series";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("List data series (columns) of an open data file. "
                + "Returns series name, semantic, unit, visibility, read-only status and, when the "
                + "file template defines one, a description of what the series measures. "
                + "Read the descriptions before interpreting or processing the data.");
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
            ArrayNode series = mapper.createArrayNode();
            ColumnSchema schema = GeoData.getSchema(dataFile.getGeoData());
            if (schema != null) {
                DataMapping mapping = dataMapping(dataFile);
                for (Column column : schema) {
                    ObjectNode node = series.addObject();
                    node.put("name", column.getHeader());
                    if (!Strings.isNullOrEmpty(column.getSemantic())) {
                        node.put("semantic", column.getSemantic());
                    }
                    if (!Strings.isNullOrEmpty(column.getUnit())) {
                        node.put("unit", column.getUnit());
                    }
                    String description = seriesDescription(mapping, column.getHeader());
                    if (description != null) {
                        node.put("description", description);
                    }
                    node.put("visible", column.isDisplay());
                    node.put("readOnly", column.isReadOnly());
                }
            }
            return toJson(series);
        }));
    }
}
