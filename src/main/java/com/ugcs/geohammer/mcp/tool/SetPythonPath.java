package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.script.PythonInterpreter;
import com.ugcs.geohammer.util.Strings;
import java.io.File;

public class SetPythonPath extends McpTool {

    private final PythonInterpreter pythonInterpreter;

    public SetPythonPath(Model model, PythonInterpreter pythonInterpreter) {
        super(model);
        this.pythonInterpreter = pythonInterpreter;
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
            pythonInterpreter.setPath(path);
        }
        String currentPath;
        try {
            currentPath = pythonInterpreter.getPath().toString();
        } catch (Exception e) {
            return text("Python interpreter is not configured: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        try {
            pythonInterpreter.checkVersion();
            return text("Python interpreter: " + currentPath + " (version check passed, 3.8 or newer)");
        } catch (Exception e) {
            return text("Python interpreter: " + currentPath + "; version check failed: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }
}
