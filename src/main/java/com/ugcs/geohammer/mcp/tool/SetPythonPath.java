package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.script.PythonService;
import com.ugcs.geohammer.util.Strings;
import java.io.File;

public class SetPythonPath extends McpTool {

    private final PythonService pythonService;

    public SetPythonPath(Model model, PythonService pythonService) {
        super(model);
        this.pythonService = pythonService;
    }

    @Override
    public String getName() {
        return "set_python_path";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Get or set the Python interpreter used to run scripts. "
                + "Call without arguments to report the current interpreter path and version; "
                + "pass path to switch to a different interpreter (Python 3.8 or newer). "
                + "The setting persists in the application preferences.");
        ObjectNode schema = objectSchema();
        addProperty(schema, "path", "string",
                "Full path of the python executable; omit to only report the current one.");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String path = optionalString(args, "path");
        if (!Strings.isNullOrEmpty(path)) {
            File executable = new File(path);
            if (!executable.isFile()) {
                throw new IllegalArgumentException("Python executable not found at: " + path);
            }
            pythonService.setPythonPath(path);
        }
        String currentPath;
        try {
            currentPath = pythonService.getPythonPath().toString();
        } catch (Exception e) {
            return text("Python interpreter is not configured: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        try {
            pythonService.checkVersion();
            return text("Python interpreter: " + currentPath + " (version check passed, 3.8 or newer)");
        } catch (Exception e) {
            return text("Python interpreter: " + currentPath + "; version check failed: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }
}
