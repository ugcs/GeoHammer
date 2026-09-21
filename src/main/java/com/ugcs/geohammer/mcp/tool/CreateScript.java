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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class CreateScript extends ScriptTool {

    private static final String DEFAULT_TEMPLATE = "csv";

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
                + "\n\nScript contract. A script is <name>.py plus a generated <name>.json with the "
                + "metadata given here (no requirements file: dependencies are detected from imports "
                + "and installed on first run; pandas, numpy and scipy are commonly used). GeoHammer "
                + "invokes it as: python <name>.py <file> --<param> <value> ... --<flag>. "
                + "The first positional argument is a temporary copy of the open file: a CSV with a "
                + "header row for CSV files (the separator is the one of the file's template, so "
                + "detect it), a SEG-Y file for GPR files, an SVLOG file for sonar logs. Every declared "
                + "parameter is passed as "
                + "--<name> <value> with the parameter name used verbatim (a name like clear-marks "
                + "becomes --clear-marks); BOOLEAN parameters are passed as a bare --<name> flag only "
                + "when true and are absent when false, so a BOOLEAN must default to \"false\" and "
                + "the script must treat the missing flag as off. Parse with argparse: parser.add_argument(\"file_path\") plus one "
                + "add_argument(\"--<name>\") per declared parameter (action=\"store_true\" for "
                + "BOOLEAN), so the parameter list in the metadata and the argparse options must match "
                + "exactly. "
                + "\n\nTo modify data, read the file, process it and write the result back to the "
                + "same path; GeoHammer loads the result back into the app (undoable). Keep the header "
                + "and all existing columns and add new columns rather than replacing the table, so the "
                + "result still matches the file's template. For CSV use the shared helper: "
                + "from script_utils import detect_separator; sep = detect_separator(args.file_path); "
                + "df = pd.read_csv(args.file_path, sep=sep); ... "
                + "df.to_csv(args.file_path, index=False, sep=sep). "
                + "To return results without changing data, print to stdout and leave the file "
                + "unmodified; printed lines are shown to the user. A script with a LINE_INDEX parameter "
                + "receives only the selected survey line and its output file is never loaded back "
                + "(analysis only). Exit with a non-zero code to report a failure.");
        ObjectNode schema = objectSchema();
        addProperty(schema, "name", "string",
                "Script file name without extension; letters, digits, underscore and dash only.");
        addProperty(schema, "display_name", "string",
                "Human-readable name shown in the UI; defaults to the file name.");
        addProperty(schema, "code", "string", "Python source code of the script.");
        ObjectNode createParams = addProperty(schema, "parameters", "array",
                "Declared script parameters in UI order; each one is passed to the script as the "
                + "--name option. In the UI, COLUMN_NAME is a dropdown of the file's data columns, "
                + "ENUM a dropdown of enum_values, LINE_INDEX a survey line selector, FOLDER_PATH a "
                + "folder chooser that defaults to the data file's folder (use it for export "
                + "destinations), FILE_PATH a file chooser, STRING a plain text field, "
                + "BOOLEAN a checkbox. FILE_PATH and FOLDER_PATH values must point to an existing "
                + "file or folder, so do not use them for outputs the script creates itself.");
        createParams.set("items", parameterSchema());
        ObjectNode createTemplates = addProperty(schema, "templates", "array",
                "File templates the script applies to; the script is offered in the UI only for files "
                + "whose template is listed. Use \"csv\" for any CSV file (the default when omitted or "
                + "empty), \"sgy\" for GPR/SEG-Y files, \"dzt\" for GSSI GPR files, \"SVLOG\" for "
                + "sonar logs, or specific CSV template names as reported by {{list_files}} "
                + "(for example \"MagNIMBUS\", \"MagDrone R3\") to restrict the script to those.");
        createTemplates.putObject("items").put("type", "string");
        addProperty(schema, "overwrite", "boolean",
                "Set true to replace an existing script with the same name, default false.");
        schema.putArray("required").add("name").add("code");
        tool.set("inputSchema", schema);
        return tool;
    }

    private static ObjectNode parameterSchema() {
        ObjectNode item = objectSchema();
        addProperty(item, "name", "string",
                "Option name passed to the script as --name; letters, digits and dashes.");
        addProperty(item, "display_name", "string", "Label shown in the UI; defaults to name.");
        ObjectNode type = addProperty(item, "type", "string", "Parameter type.");
        ArrayNode types = type.putArray("enum");
        for (ScriptParameter.ParameterType parameterType : ScriptParameter.ParameterType.values()) {
            types.add(parameterType.name());
        }
        addProperty(item, "default_value", "string",
                "Initial value as a string, such as \"10\", \"5.0\", \"MEDIAN\" or \"false\"; "
                + "give one whenever a sensible default exists.");
        addProperty(item, "required", "boolean", "Whether a value must be provided, default false.");
        ObjectNode enumValues = addProperty(item, "enum_values", "array",
                "Allowed values, required for ENUM.");
        enumValues.putObject("items").put("type", "string");
        addProperty(item, "min", "number", "Lower bound for INTEGER and DOUBLE.");
        addProperty(item, "max", "number", "Upper bound for INTEGER and DOUBLE.");
        item.putArray("required").add("name").add("type");
        item.put("additionalProperties", false);
        return item;
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
                if (parameter instanceof ObjectNode parameterObject && parameterObject.hasNonNull("type")) {
                    // tolerate "integer" for INTEGER
                    parameterObject.put("type", parameterObject.get("type").asText().toUpperCase(Locale.ROOT));
                }
                parameters.add(parameter);
            }
        }
        ArrayNode templates = metadataNode.putArray("templates");
        JsonNode templatesNode = args.get("templates");
        if (templatesNode != null && templatesNode.isArray()) {
            for (JsonNode template : templatesNode) {
                if (template.isTextual() && !template.asText().isBlank()) {
                    templates.add(template.asText().trim());
                }
            }
        }
        if (templates.isEmpty()) {
            // a script with no templates is never offered in the UI; default to any CSV file
            templates.add(DEFAULT_TEMPLATE);
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
        // the Scripts panel discovers scripts by their .json, so the .py must be in place first
        Files.writeString(scriptFile, code);
        Files.writeString(metadataFile,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadataNode));
        return text("Script created: " + scriptFile
                + "; run it with run_script \"" + name + ".py\"");
    }
}
