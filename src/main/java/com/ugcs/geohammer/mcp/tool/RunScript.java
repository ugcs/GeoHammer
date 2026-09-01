package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.script.ScriptCoordinator;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptPaths;
import com.ugcs.geohammer.service.script.ScriptRunListener;
import com.ugcs.geohammer.service.script.ScriptValidationException;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Templates;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class RunScript extends ScriptTool {

    private static final int SCRIPT_TIMEOUT_MINUTES = 15;

    private static final int MAX_SCRIPT_OUTPUT_CHARS = 20000;

    private final ScriptCoordinator scriptCoordinator;

    public RunScript(Model model, ScriptMetadataLoader scriptMetadataLoader,
                     ScriptPaths scriptPaths, ScriptCoordinator scriptCoordinator) {
        super(model, scriptMetadataLoader, scriptPaths);
        this.scriptCoordinator = scriptCoordinator;
    }

    @Override
    public String getName() {
        return "run_script";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Run a processing script on an open file and wait for completion. "
                + "ONLY use when the user explicitly asked for a script to be run, either naming the "
                + "script or asking to choose an appropriate one. Never replace the regular data tools "
                + "with a script on your own initiative. "
                + "The file is exported to a temporary CSV, the script runs on it with the given "
                + "parameters, the modified file is loaded back (undoable, the file on disk is not "
                + "changed) and the captured script output (stdout) is returned. "
                + "May take minutes on large files; missing Python dependencies are installed "
                + "automatically on first use.");
        ObjectNode schema = objectSchema();
        addProperty(schema, "script", "string",
                "Script file name from {{list_scripts}}, with or without the .py extension.");
        addFileProperty(schema);
        ObjectNode scriptParams = addProperty(schema, "params", "object",
                "Script parameters as name-value pairs; see the script's parameter list "
                + "in {{list_scripts}} for names, types and which are required.");
        scriptParams.putObject("additionalProperties");
        schema.putArray("required").add("script");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String scriptName = requiredString(args, "script");
        String fileName = optionalString(args, "file");
        ScriptMetadata metadata = findScript(scriptName);

        Map<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = args.get("params");
        if (paramsNode != null && paramsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                params.put(field.getKey(), value.isTextual() ? value.asText() : value.toString());
            }
        }
        try {
            metadata.validateRequiredParameters(params);
        } catch (ScriptValidationException e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        SgyFile dataFile = inFxThread(() -> resolveFile(fileName));
        String template = Templates.getTemplateName(dataFile);
        if (!Nulls.toEmpty(metadata.templates()).isEmpty()
                && !containsIgnoreCase(metadata.templates(), template)) {
            throw new IllegalArgumentException("Script does not apply to this file: it supports "
                    + "templates " + metadata.templates() + ", the file's template is " + template);
        }

        StringBuilder output = new StringBuilder();
        CompletableFuture<String> completion = new CompletableFuture<>();
        scriptCoordinator.submit(List.of(dataFile), metadata, params,
                line -> {
                    synchronized (output) {
                        output.append(line).append('\n');
                    }
                },
                new ScriptRunListener() {
                    @Override
                    public void onRunStarted() {
                    }

                    @Override
                    public void onRunFinished() {
                        // resolves the future when no per-file callback fired,
                        // e.g. the run was skipped or cancelled
                        completion.complete("Script run finished");
                    }

                    @Override
                    public void onSuccess(ScriptMetadata scriptMetadata) {
                        completion.complete("Script completed");
                    }

                    @Override
                    public void onError(ScriptMetadata scriptMetadata, Exception e, String scriptOutput) {
                        String message = e.getMessage() != null ? e.getMessage() : e.toString();
                        completion.completeExceptionally(new IllegalStateException(
                                "Script failed: " + message));
                    }

                    @Override
                    public boolean confirmReinstallDependencies(String moduleName) {
                        return true;
                    }
                });

        String status;
        try {
            status = completion.get(SCRIPT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(cause.getMessage() + outputTail(output), cause);
        }
        return text(status + outputTail(output));
    }

    private static boolean containsIgnoreCase(List<String> values, @Nullable String value) {
        for (String candidate : Nulls.toEmpty(values)) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static String outputTail(StringBuilder output) {
        String text;
        synchronized (output) {
            text = output.toString().strip();
        }
        if (text.isEmpty()) {
            return "";
        }
        if (text.length() > MAX_SCRIPT_OUTPUT_CHARS) {
            text = "..." + text.substring(text.length() - MAX_SCRIPT_OUTPUT_CHARS);
        }
        return "\n\nScript output:\n" + text;
    }
}
