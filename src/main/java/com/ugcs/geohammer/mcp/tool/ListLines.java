package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;

import java.util.Map;

public class ListLines extends McpTool {

    public ListLines(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "list_lines";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("List survey lines of an open data file "
                + "with their point index ranges.");
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
            ArrayNode lines = mapper.createArrayNode();
            for (Map.Entry<Integer, IndexRange> entry : dataFile.getLineRanges().entrySet()) {
                IndexRange range = entry.getValue();
                ObjectNode node = lines.addObject();
                node.put("line", entry.getKey());
                node.put("from", range.from());
                node.put("to", range.to());
                node.put("points", range.to() - range.from());
            }
            return toJson(lines);
        }));
    }
}
