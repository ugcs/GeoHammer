package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpSession;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;

public class ListFiles extends McpTool {

    public ListFiles(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("List files opened in GeoHammer. Returns for each file: name, path, "
                + "type, template (name of the format template the file was parsed with), "
                + "number of points (for GPR files: traces), unsaved status and busy status. "
                + "A busy file is in use by a call of another client: "
                + "calls on it wait for the file and fail if it stays busy. "
                + "The type is \"csv\", \"sonar\" (SVLOG) or \"nmea\" for data files, which hold a "
                + "sequence of points, and \"gpr\" for ground penetrating radar files (SGY, DZT), "
                + "which hold a sequence of traces. "
                + "Series and filter tools work on data files; GPR tools work on \"gpr\" files; "
                + "marks, line and crop tools work on both.");
        tool.set("inputSchema", objectSchema());
        return tool;
    }

    @Override
    public ObjectNode invoke(McpSession session, JsonNode args) throws Exception {
        ArrayNode files = mapper.createArrayNode();
        for (SgyFile dataFile : dataFiles()) {
            files.add(fileDescriptor(dataFile));
        }
        return text(toJson(files));
    }
}
