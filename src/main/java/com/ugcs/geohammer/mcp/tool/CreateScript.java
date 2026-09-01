package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.service.script.ScriptPaths;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class CreateScript extends ScriptTool {

    public CreateScript(Model model, ScriptMetadataLoader scriptMetadataLoader, ScriptPaths scriptPaths) {
        super(model, scriptMetadataLoader, scriptPaths);
    }

    @Override
    public String getName() {
        return "create_script";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Create (or overwrite) a reusable processing script in the "
                + "GeoHammer scripts folder, making it available to {{run_script}} and to the Scripts tool "
                + "in the UI. "
                + "ONLY use when the user explicitly asks to store an algorithm as a GeoHammer script; "
                + "the usual case is that a result has already been achieved in the session and the "
                + "user then asks to save that work as a script. Scripts are a user-facing library, "
                + "not a scratchpad: never create one to hold intermediate results, temporary code, or "
                + "a step of a task the user did not ask to keep. If unsure whether the user wants a "
                + "script saved, ask instead of creating one. "
                + "The script must be a Python program that takes the path of a CSV file as "
                + "its first positional argument, plus the declared parameters as --name value options "
                + "(boolean parameters are passed as a flag without a value when true). To return "
                + "results without changing data, print to stdout and leave the file unmodified. "
                + "Common libraries (pandas, numpy, scipy) may be imported; missing ones are installed "
                + "on first run.");
        ObjectNode schema = objectSchema();
        addProperty(schema, "name", "string",
                "Script file name without extension; letters, digits, underscore and dash only.");
        addProperty(schema, "display_name", "string",
                "Human-readable name shown in the UI; defaults to the file name.");
        addProperty(schema, "code", "string", "Python source code of the script.");
        ObjectNode createParams = addProperty(schema, "parameters", "array",
                "Declared script parameters. Each item: {name, display_name?, type "
                + "(STRING | INTEGER | DOUBLE | BOOLEAN | FILE_PATH | FOLDER_PATH | COLUMN_NAME | ENUM), "
                + "default_value?, required?, enum_values? (for ENUM), min?, max? (for numbers)}.");
        createParams.putObject("items").put("type", "object");
        ObjectNode createTemplates = addProperty(schema, "templates", "array",
                "File templates the script applies to (template values from {{list_files}}); "
                + "empty or omitted means any file.");
        createTemplates.putObject("items").put("type", "string");
        addProperty(schema, "overwrite", "boolean",
                "Set true to replace an existing script with the same name, default false.");
        schema.putArray("required").add("name").add("code");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String name = requiredString(args, "name");
        if (!name.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Script name may contain only letters, digits, "
                    + "underscore and dash");
        }
        String code = requiredString(args, "code");
        String displayName = optionalString(args, "display_name");
        boolean overwrite = args.path("overwrite").asBoolean(false);

        ObjectNode metadataNode = mapper.createObjectNode();
        metadataNode.put("filename", name + ".py");
        metadataNode.put("display_name", !Strings.isNullOrEmpty(displayName) ? displayName : name);
        ArrayNode parameters = metadataNode.putArray("parameters");
        JsonNode parametersNode = args.get("parameters");
        if (parametersNode != null && parametersNode.isArray()) {
            for (JsonNode parameter : parametersNode) {
                parameters.add(parameter);
            }
        }
        ArrayNode templates = metadataNode.putArray("templates");
        JsonNode templatesNode = args.get("templates");
        if (templatesNode != null && templatesNode.isArray()) {
            for (JsonNode template : templatesNode) {
                templates.add(template);
            }
        }

        // validate metadata by parsing it the same way the app does
        ScriptMetadata metadata = mapper.treeToValue(metadataNode, ScriptMetadata.class);
        for (ScriptParameter parameter : Nulls.toEmpty(metadata.parameters())) {
            parameter.validate();
        }

        Path scriptsDir = scriptPaths.getScriptsPath();
        Path scriptFile = scriptsDir.resolve(name + ".py");
        Path metadataFile = scriptsDir.resolve(name + ".json");
        if (!overwrite && (Files.exists(scriptFile) || Files.exists(metadataFile))) {
            throw new IllegalArgumentException("Script already exists: " + name
                    + "; pass overwrite: true to replace it");
        }
        Files.createDirectories(scriptsDir);
        Files.writeString(scriptFile, code);
        Files.writeString(metadataFile,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadataNode));
        return text("Script created: " + scriptFile
                + "; run it with run_script \"" + name + ".py\"");
    }
}
