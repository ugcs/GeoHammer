package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.Loader;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.csv.parser.Parser;
import com.ugcs.geohammer.format.csv.parser.Warnings;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Result;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OpenFile extends McpTool {

    private static final int LOAD_TIMEOUT_SECONDS = 120;

    private final Loader loader;

    public OpenFile(Model model, Loader loader) {
        super(model);
        this.loader = loader;
    }

    @Override
    public String getName() {
        return "open_file";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Open a data file in GeoHammer, same as opening it from the app menu. "
                + "Supported formats: CSV, SGY, DZT, SVLOG, NMEA. The file is parsed, added to the open "
                + "files ({{list_files}}) and becomes the active file. A file that is already open is "
                + "not reloaded, only made active. Returns the descriptor of the opened file: name, path, "
                + "type, template, number of points and unsaved status, followed by parser warnings "
                + "if any. Fails with the parser error when the file cannot be opened. Opening never "
                + "shows dialogs in the app; a CSV file without a matching template fails, the user "
                + "has to open it manually in the app to create the template.");
        ObjectNode schema = objectSchema();
        addProperty(schema, "path", "string", "Absolute path of the file on the machine running GeoHammer.");
        schema.putArray("required").add("path");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String path = requiredString(args, "path");
        File file = new File(path).getAbsoluteFile();
        if (file.isDirectory()) {
            throw new IllegalArgumentException("Path is a directory, specify a file: " + file);
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("File not found: " + file);
        }

        String alreadyOpen = inFxThread(() -> {
            SgyFile openFile = model.getFileManager().getFile(file);
            if (openFile == null) {
                return null;
            }
            Chart chart = model.getChart(openFile);
            if (chart != null) {
                model.selectAndScrollToChart(chart);
            }
            return "File is already open\n" + toJson(fileDescriptor(openFile));
        });
        if (alreadyOpen != null) {
            return text(alreadyOpen);
        }

        // the loader builds its progress UI, so it has to be started from the FX thread
        Future<Map<File, Result<File>>> loading = inFxThread(() -> loader.load(List.of(file), false));
        Map<File, Result<File>> results;
        try {
            results = loading.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Opening " + file.getName() + " is still in progress; "
                    + "check {{list_files}} later");
        }
        Result<File> result = results.get(file);
        if (result == null) {
            throw new IllegalStateException("Unknown result of opening " + file.getName()
                    + ": no attempt to open the file was made");
        }
        if (result.isError()) {
            Exception error = result.error();
            throw new IllegalArgumentException("Can't open file " + file.getName() + ": " + error.getMessage(), error);
        }

        // the loader reports the file it opened: a meta file resolves to its data file
        File openedFile = result.value();
        if (openedFile == null) {
            throw new IllegalStateException("Unknown result of opening " + file.getName()
                    + ": no opened file reported");
        }
        // the chart is initialized in a queued FX task; reading in the FX thread runs after it
        return text(inFxThread(() -> {
            SgyFile dataFile = model.getFileManager().getFile(openedFile);
            if (dataFile == null) {
                throw new IllegalStateException("File was not added to the open files: " + openedFile.getName());
            }
            String response = toJson(fileDescriptor(dataFile));
            Warnings warnings = parserWarnings(dataFile);
            if (warnings != null && !warnings.isEmpty()) {
                response += "\nWarnings:\n" + warnings.format();
            }
            return response;
        }));
    }

    @Nullable
    private static Warnings parserWarnings(SgyFile dataFile) {
        if (dataFile instanceof CsvFile csvFile) {
            Parser parser = csvFile.getParser();
            return parser != null ? parser.getWarnings() : null;
        }
        return null;
    }
}
