package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptPaths;
import java.util.List;

public class ListScripts extends ScriptTool {

    public ListScripts(Model model, ScriptMetadataLoader scriptMetadataLoader, ScriptPaths scriptPaths) {
        super(model, scriptMetadataLoader, scriptPaths);
    }

    @Override
    public String getName() {
        return "list_scripts";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("List processing scripts available in GeoHammer. "
                + "ONLY call this when the user explicitly mentions scripts, for example asks to run a "
                + "script, to pick a suitable script, or to see which scripts exist. Do NOT call it to "
                + "look for a shortcut for an ordinary request: normal tasks are done with the other "
                + "tools, not with scripts. "
                + "Scripts are Python programs stored in the GeoHammer scripts folder; they receive a "
                + "copy of an open file as CSV, may modify it (the result is loaded back, with undo) "
                + "and may print results to stdout. Each script declares typed parameters and the file "
                + "templates it applies to (matched against the file's template from {{list_files}}; an "
                + "empty list means any file).");
        ObjectNode schema = objectSchema();
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        List<ScriptMetadata> scripts = scriptMetadataLoader
                .loadScriptMetadata(scriptPaths.getScriptsPath());
        ArrayNode result = mapper.createArrayNode();
        for (ScriptMetadata metadata : scripts) {
            result.add(describeScript(metadata));
        }
        return text(toJson(result));
    }
}
