package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptPaths;
import java.nio.file.Files;
import java.nio.file.Path;

public class GetScript extends ScriptTool {

    public GetScript(Model model, ScriptMetadataLoader scriptMetadataLoader, ScriptPaths scriptPaths) {
        super(model, scriptMetadataLoader, scriptPaths);
    }

    @Override
    public String getName() {
        return "get_script";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Read the Python source code and metadata of a script, "
                + "see {{list_scripts}} for available scripts. "
                + "Only for a script the user asked to run or inspect.");
        ObjectNode schema = objectSchema();
        addProperty(schema, "script", "string",
                "Script file name from {{list_scripts}}, with or without the .py extension.");
        schema.putArray("required").add("script");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String scriptName = requiredString(args, "script");
        ScriptMetadata metadata = findScript(scriptName);
        Path scriptFile = scriptPaths.getScriptsPath().resolve(metadata.filename());
        ObjectNode result = describeScript(metadata);
        result.put("code", Files.readString(scriptFile));
        return text(toJson(result));
    }
}
