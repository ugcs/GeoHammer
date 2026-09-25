package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.mcp.McpCall;
import com.ugcs.geohammer.mcp.McpSession;
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
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RunScript extends ScriptTool {

    // a client aborts a call that stays silent for a few minutes
    private static final int PROGRESS_INTERVAL_SECONDS = 10;

    private static final int MAX_SCRIPT_OUTPUT_CHARS = 20000;

    private static final int MAX_PROGRESS_LINE_CHARS = 200;

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
                + "The file is saved to a temporary copy in its own format (CSV for CSV files, SEG-Y "
                + "for GPR files, SVLOG for sonar logs), the script runs on it with the given "
                + "parameters, the modified copy is loaded back (undoable, the file on disk is not "
                + "changed) and the captured script output (stdout) is returned. "
                + "May take minutes on large files; missing Python dependencies are installed "
                + "automatically on first use.");
        ObjectNode schema = objectSchema();
        addProperty(schema, "script", "string",
                "Script file name from {{list_scripts}}, with or without the .py extension.");
        addFileProperty(schema);
        ObjectNode scriptParams = addProperty(schema, "params", "object",
                "Script parameters as name-value pairs; see the script's parameter list "
                + "in {{list_scripts}} for names, types and which are required. Values by type: "
                + "STRING any text; INTEGER a whole number, DOUBLE a number, both within min/max when "
                + "given; BOOLEAN true or false; ENUM exactly one of the listed enumValues; "
                + "COLUMN_NAME a series name of the file from {{list_series}}; LINE_INDEX a line "
                + "number of the file from {{list_lines}}; FILE_PATH an absolute path of an existing "
                + "file on this machine, FOLDER_PATH an absolute path of an existing folder.");
        scriptParams.putObject("additionalProperties");
        schema.withArrayProperty("required").add("script");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    protected boolean modifiesFiles() {
        return true;
    }

    @Override
    public ObjectNode invoke(McpSession session, JsonNode args) throws Exception {
        return invoke(session, args, new McpCall(null));
    }

    @Override
    public ObjectNode invoke(McpSession session, JsonNode args, McpCall call) throws Exception {
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

        SgyFile dataFile = resolveFile(fileName);
        String template = Templates.getTemplateName(dataFile);
        if (!appliesTo(metadata, dataFile, template)) {
            throw new IllegalArgumentException("Script does not apply to this file: it supports "
                    + "templates " + metadata.templates() + ", the file's template is " + template);
        }

        if (scriptCoordinator.isExecuting(dataFile)) {
            throw new IllegalStateException(alreadyRunning(dataFile));
        }

        Run run = new Run();
        Future<Void> future = scriptCoordinator.submit(List.of(dataFile), metadata, params,
                run::appendOutput, run);
        call.onCancel(() -> run.cancel(future));

        // the file stays locked until the run finishes, also when it is cancelled
        long startTime = System.nanoTime();
        try {
            while (!run.awaitFinish(PROGRESS_INTERVAL_SECONDS)) {
                // a run cancelled in the app before it started never finishes
                if (future.isCancelled()) {
                    run.cancel(future);
                }
                Duration elapsed = Duration.ofNanos(System.nanoTime() - startTime);
                call.reportProgress(elapsed.toSeconds(), progressMessage(elapsed, run.lastLine));
            }
        } catch (InterruptedException e) {
            run.cancel(future);
            throw e;
        }

        String outputTail = outputTail(run.output);
        // a result applied before the cancellation took effect is kept
        if (run.succeeded.get()) {
            return text("Script completed" + outputTail);
        }
        if (future.isCancelled()) {
            throw new IllegalStateException("Script run was cancelled" + outputTail);
        }
        String error = run.error.get();
        if (error != null) {
            throw new IllegalStateException(error + outputTail);
        }
        // the coordinator skips a file with a running script
        throw new IllegalStateException(alreadyRunning(dataFile));
    }

    private String alreadyRunning(SgyFile file) {
        ScriptMetadata running = scriptCoordinator.getExecutingScriptMetadata(file);
        return "Another script" + (running != null ? " (" + running.filename() + ")" : "")
                + " is running on this file; run again when it finishes";
    }

    // mirrors the Scripts panel: a file without a template accepts any script,
    // otherwise the template must be listed, with "csv" matching any CSV file
    private static boolean appliesTo(ScriptMetadata metadata, SgyFile file, @Nullable String template) {
        if (template == null) {
            return true;
        }
        List<String> templates = Nulls.toEmpty(metadata.templates());
        return containsIgnoreCase(templates, template)
                || (file instanceof CsvFile && containsIgnoreCase(templates, "csv"));
    }

    private static boolean containsIgnoreCase(List<String> values, @Nullable String value) {
        for (String candidate : Nulls.toEmpty(values)) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static String progressMessage(Duration elapsed, @Nullable String lastLine) {
        String message = "Running for " + elapsed.toMinutes() + " min " + elapsed.toSecondsPart() + " s";
        if (lastLine != null) {
            message += ": " + (lastLine.length() > MAX_PROGRESS_LINE_CHARS
                    ? lastLine.substring(0, MAX_PROGRESS_LINE_CHARS) + "..."
                    : lastLine);
        }
        return message;
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

    // a script run on a single file as seen by the call
    private static final class Run implements ScriptRunListener {

        private final StringBuilder output = new StringBuilder();

        private final AtomicBoolean started = new AtomicBoolean();

        private final AtomicBoolean succeeded = new AtomicBoolean();

        private final AtomicReference<String> error = new AtomicReference<>();

        private final CountDownLatch finished = new CountDownLatch(1);

        @Nullable
        private volatile String lastLine;

        void appendOutput(String line) {
            synchronized (output) {
                output.append(line).append('\n');
                // only the tail is returned
                if (output.length() > 2 * MAX_SCRIPT_OUTPUT_CHARS) {
                    output.delete(0, output.length() - MAX_SCRIPT_OUTPUT_CHARS);
                }
            }
            if (!line.isBlank()) {
                lastLine = line.strip();
            }
        }

        boolean awaitFinish(int seconds) throws InterruptedException {
            return finished.await(seconds, TimeUnit.SECONDS);
        }

        // a run cancelled before it started never finishes, and one starting
        // at this moment is interrupted and stops before it touches the file
        void cancel(Future<Void> future) {
            future.cancel(true);
            if (!started.get()) {
                finished.countDown();
            }
        }

        @Override
        public void onRunStarted() {
            started.set(true);
        }

        // finishes after the undo frame of the run is pushed,
        // so that the frame is attributed to the session
        @Override
        public void onRunFinished() {
            finished.countDown();
        }

        @Override
        public void onSuccess(ScriptMetadata scriptMetadata) {
            succeeded.set(true);
        }

        @Override
        public void onError(ScriptMetadata scriptMetadata, Exception e, String scriptOutput) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            error.compareAndSet(null, "Script failed: " + message);
        }

        @Override
        public boolean confirmReinstallDependencies(String moduleName) {
            return true;
        }
    }
}
