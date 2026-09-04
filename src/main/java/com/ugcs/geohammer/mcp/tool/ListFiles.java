package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Templates;
import java.io.File;

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
                + "number of points (for GPR files: traces) and unsaved status. "
                + "The type is \"csv\", \"sonar\" (SVLOG) or \"nmea\" for data files, which hold a "
                + "sequence of points, and \"gpr\" for ground penetrating radar files (SGY, DZT), "
                + "which hold a sequence of traces. "
                + "Series and filter tools work on data files; GPR tools work on \"gpr\" files; "
                + "marks, line and crop tools work on both.");
        tool.set("inputSchema", objectSchema());
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        return text(inFxThread(() -> {
            ArrayNode files = mapper.createArrayNode();
            for (SgyFile dataFile : dataFiles()) {
                File file = dataFile.getFile();
                ObjectNode node = files.addObject();
                node.put("name", file != null ? file.getName() : null);
                node.put("path", file != null ? file.getAbsolutePath() : null);
                node.put("type", fileType(dataFile));
                node.put("template", Templates.getTemplateName(dataFile));
                node.put("points", dataFile.numTraces());
                node.put("unsaved", dataFile.isUnsaved());
            }
            return toJson(files);
        }));
    }
}
