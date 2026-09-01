package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.service.script.ScriptPaths;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Tools that read the GeoHammer scripts folder.
public abstract class ScriptTool extends McpTool {

    protected final ScriptMetadataLoader scriptMetadataLoader;

    protected final ScriptPaths scriptPaths;

    protected ScriptTool(Model model, ScriptMetadataLoader scriptMetadataLoader, ScriptPaths scriptPaths) {
        super(model);
        this.scriptMetadataLoader = scriptMetadataLoader;
        this.scriptPaths = scriptPaths;
    }

    protected ScriptMetadata findScript(String name) throws IOException {
        String fileName = name.endsWith(".py") ? name : name + ".py";
        List<ScriptMetadata> scripts = scriptMetadataLoader
                .loadScriptMetadata(scriptPaths.getScriptsPath());
        List<String> names = new ArrayList<>(scripts.size());
        for (ScriptMetadata metadata : scripts) {
            if (metadata.filename().equalsIgnoreCase(fileName)
                    || metadata.displayName().equalsIgnoreCase(name)) {
                return metadata;
            }
            names.add(metadata.filename());
        }
        throw new IllegalArgumentException("Script not found: " + name
                + "; available scripts: " + String.join(", ", names));
    }

    protected ObjectNode describeScript(ScriptMetadata metadata) {
        ObjectNode node = mapper.createObjectNode();
        node.put("script", metadata.filename());
        node.put("displayName", metadata.displayName());
        ArrayNode templates = node.putArray("templates");
        for (String template : Nulls.toEmpty(metadata.templates())) {
            templates.add(template);
        }
        node.put("supportedOnThisOs", metadata.supportsCurrentOs());
        ArrayNode parameters = node.putArray("parameters");
        for (ScriptParameter parameter : Nulls.toEmpty(metadata.parameters())) {
            ObjectNode parameterNode = parameters.addObject();
            parameterNode.put("name", parameter.name());
            parameterNode.put("displayName", parameter.displayName());
            parameterNode.put("type", parameter.type() != null ? parameter.type().name() : null);
            parameterNode.put("required", parameter.required());
            if (!Strings.isNullOrEmpty(parameter.defaultValue())) {
                parameterNode.put("defaultValue", parameter.defaultValue());
            }
            if (parameter.enumValues() != null && !parameter.enumValues().isEmpty()) {
                ArrayNode enumValues = parameterNode.putArray("enumValues");
                for (String value : parameter.enumValues()) {
                    enumValues.add(value);
                }
            }
            if (parameter.min() != null) {
                parameterNode.put("min", parameter.min());
            }
            if (parameter.max() != null) {
                parameterNode.put("max", parameter.max());
            }
        }
        return node;
    }
}
