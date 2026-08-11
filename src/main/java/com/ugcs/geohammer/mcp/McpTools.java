package com.ugcs.geohammer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.element.PositionalObject;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.service.TraceTransform;
import com.ugcs.geohammer.service.gridding.GriddingFilter;
import com.ugcs.geohammer.service.gridding.GriddingParams;
import com.ugcs.geohammer.service.gridding.GriddingResult;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.service.palette.PaletteType;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.service.script.PythonService;
import com.ugcs.geohammer.service.script.ScriptCoordinator;
import com.ugcs.geohammer.service.script.ScriptMetadata;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptParameter;
import com.ugcs.geohammer.service.script.ScriptPaths;
import com.ugcs.geohammer.service.script.ScriptRunListener;
import com.ugcs.geohammer.service.script.ScriptValidationException;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class McpTools {

    private static final Logger log = LoggerFactory.getLogger(McpTools.class);

    private static final int DEFAULT_READ_COUNT = 1000;
    private static final int MAX_READ_COUNT = 10000;
    private static final int CALL_TIMEOUT_SECONDS = 30;
    private static final int SCRIPT_TIMEOUT_MINUTES = 15;
    private static final int MAX_SCRIPT_OUTPUT_CHARS = 20000;

    private final Model model;

    private final UndoModel undoModel;

    private final TraceTransform traceTransform;

    private final GriddingService griddingService;

    private final GridLayer gridLayer;

    private final ScriptCoordinator scriptCoordinator;

    private final ScriptMetadataLoader scriptMetadataLoader;

    private final ScriptPaths scriptPaths;

    private final PythonService pythonService;

    private final ObjectMapper mapper = new ObjectMapper();

    public McpTools(Model model, UndoModel undoModel, TraceTransform traceTransform,
                    GriddingService griddingService, GridLayer gridLayer,
                    ScriptCoordinator scriptCoordinator, ScriptMetadataLoader scriptMetadataLoader,
                    ScriptPaths scriptPaths, PythonService pythonService) {
        this.model = model;
        this.undoModel = undoModel;
        this.traceTransform = traceTransform;
        this.griddingService = griddingService;
        this.gridLayer = gridLayer;
        this.scriptCoordinator = scriptCoordinator;
        this.scriptMetadataLoader = scriptMetadataLoader;
        this.scriptPaths = scriptPaths;
        this.pythonService = pythonService;
    }

    public ArrayNode listTools() {
        ArrayNode tools = mapper.createArrayNode();

        ObjectNode listFiles = tools.addObject();
        listFiles.put("name", "list_files");
        listFiles.put("description", "List files opened in GeoHammer. Returns for each file: name, path, "
                + "kind (\"data\" for point-series files like CSV/SVLOG/NMEA, \"gpr\" for ground "
                + "penetrating radar files like SGY/DZT), template (name of the format template the file "
                + "was parsed with), number of points (for GPR files: traces) and unsaved status. "
                + "Series and filter tools work on \"data\" files; GPR tools work on \"gpr\" files; "
                + "marks, line and crop tools work on both.");
        listFiles.set("inputSchema", objectSchema());

        ObjectNode listSeries = tools.addObject();
        listSeries.put("name", "list_series");
        listSeries.put("description", "List data series (columns) of an open data file. "
                + "Returns series name, semantic, unit, visibility and read-only status.");
        ObjectNode listSeriesSchema = objectSchema();
        addFileProperty(listSeriesSchema);
        listSeries.set("inputSchema", listSeriesSchema);

        ObjectNode readSeries = tools.addObject();
        readSeries.put("name", "read_series");
        readSeries.put("description", "Read values of a data series from an open data file. "
                + "Returns up to " + MAX_READ_COUNT + " values per call; "
                + "use start and count to page through the data.");
        ObjectNode readSeriesSchema = objectSchema();
        addFileProperty(readSeriesSchema);
        addProperty(readSeriesSchema, "series", "string", "Series (column) name.");
        addProperty(readSeriesSchema, "start", "integer", "Index of the first value to read, default 0.");
        addProperty(readSeriesSchema, "count", "integer", "Number of values to read, default "
                + DEFAULT_READ_COUNT + ", maximum " + MAX_READ_COUNT + ".");
        readSeriesSchema.putArray("required").add("series");
        readSeries.set("inputSchema", readSeriesSchema);

        ObjectNode writeSeries = tools.addObject();
        writeSeries.put("name", "write_series");
        writeSeries.put("description", "Write values into a data series of an open data file "
                + "starting at the given index. Values must be numbers or nulls. "
                + "The change is shown in the app immediately, supports undo "
                + "and marks the file as unsaved.");
        ObjectNode writeSeriesSchema = objectSchema();
        addFileProperty(writeSeriesSchema);
        addProperty(writeSeriesSchema, "series", "string", "Series (column) name.");
        addProperty(writeSeriesSchema, "start", "integer", "Index of the first value to write, default 0.");
        ObjectNode values = addProperty(writeSeriesSchema, "values", "array", "Values to write.");
        values.putObject("items").putArray("type").add("number").add("null");
        writeSeriesSchema.putArray("required").add("series").add("values");
        writeSeries.set("inputSchema", writeSeriesSchema);

        ObjectNode placeMarks = tools.addObject();
        placeMarks.put("name", "place_marks");
        placeMarks.put("description", "Place marks (flags) at the given point indices of an open data file. "
                + "Marks are shown as flags on the chart and map, and are stored in the Mark column "
                + "when the file is saved. Indices where a mark already exists are skipped.");
        ObjectNode placeMarksSchema = objectSchema();
        addFileProperty(placeMarksSchema);
        ObjectNode placeIndices = addProperty(placeMarksSchema, "indices", "array",
                "Point indices to place marks at.");
        placeIndices.putObject("items").put("type", "integer");
        placeMarksSchema.putArray("required").add("indices");
        placeMarks.set("inputSchema", placeMarksSchema);

        ObjectNode clearMarks = tools.addObject();
        clearMarks.put("name", "clear_marks");
        clearMarks.put("description", "Remove marks (flags) from an open data file. "
                + "Removes all marks when indices are not specified.");
        ObjectNode clearMarksSchema = objectSchema();
        addFileProperty(clearMarksSchema);
        ObjectNode clearIndices = addProperty(clearMarksSchema, "indices", "array",
                "Point indices to remove marks at; all marks are removed when omitted.");
        clearIndices.putObject("items").put("type", "integer");
        clearMarks.set("inputSchema", clearMarksSchema);

        ObjectNode selectFile = tools.addObject();
        selectFile.put("name", "select_file");
        selectFile.put("description", "Select a file in the GeoHammer UI: scrolls its chart into view "
                + "and makes it the active file. The active file is the default target of all tools "
                + "when their file argument is omitted.");
        ObjectNode selectFileSchema = objectSchema();
        addFileProperty(selectFileSchema);
        selectFileSchema.putArray("required").add("file");
        selectFile.set("inputSchema", selectFileSchema);

        ObjectNode removeSeries = tools.addObject();
        removeSeries.put("name", "remove_series");
        removeSeries.put("description", "Delete a data series (column) and its values from an open "
                + "data file. Read-only series (positions, line numbers and other columns parsed "
                + "from the source file) cannot be deleted. Supports undo.");
        ObjectNode removeSeriesSchema = objectSchema();
        addFileProperty(removeSeriesSchema);
        addProperty(removeSeriesSchema, "series", "string", "Name of the series to delete.");
        removeSeriesSchema.putArray("required").add("series");
        removeSeries.set("inputSchema", removeSeriesSchema);

        ObjectNode selectSeries = tools.addObject();
        selectSeries.put("name", "select_series");
        selectSeries.put("description", "Select a data series in the GeoHammer UI: scrolls to the file "
                + "chart and highlights the series. The selected series is the default target "
                + "for filters and gridding.");
        ObjectNode selectSeriesSchema = objectSchema();
        addFileProperty(selectSeriesSchema);
        addProperty(selectSeriesSchema, "series", "string", "Series (column) name.");
        selectSeriesSchema.putArray("required").add("series");
        selectSeries.set("inputSchema", selectSeriesSchema);

        ObjectNode createSeries = tools.addObject();
        createSeries.put("name", "create_series");
        createSeries.put("description", "Create a new data series (column) in an open data file, "
                + "optionally filling initial values starting from index 0. "
                + "The series is shown on the chart and supports undo.");
        ObjectNode createSeriesSchema = objectSchema();
        addFileProperty(createSeriesSchema);
        addProperty(createSeriesSchema, "series", "string", "Name of the series to create.");
        addProperty(createSeriesSchema, "unit", "string", "Optional measurement unit of the series.");
        ObjectNode initValues = addProperty(createSeriesSchema, "values", "array",
                "Optional initial values, set starting from index 0.");
        initValues.putObject("items").putArray("type").add("number").add("null");
        createSeriesSchema.putArray("required").add("series");
        createSeries.set("inputSchema", createSeriesSchema);

        ObjectNode applyFilter = tools.addObject();
        applyFilter.put("name", "apply_filter");
        applyFilter.put("description", "Run a filter on a data series of an open data file. "
                + "The result is written into a new series named after the source with a filter suffix: "
                + "_LPF (lowpass), _LAG (timelag), _RM (running_median). The source series is not changed. "
                + "Filters: lowpass (value = filter length in measurements), "
                + "timelag (value = shift in measurements, may be negative), "
                + "running_median (value = window size in measurements).");
        ObjectNode applyFilterSchema = objectSchema();
        addFileProperty(applyFilterSchema);
        ObjectNode filterName = addProperty(applyFilterSchema, "filter", "string",
                "Filter to run: lowpass, timelag or running_median.");
        filterName.putArray("enum").add("lowpass").add("timelag").add("running_median");
        addProperty(applyFilterSchema, "series", "string",
                "Source series name; defaults to the series selected in the UI.");
        addProperty(applyFilterSchema, "value", "integer", "Filter parameter, see the filter list.");
        applyFilterSchema.putArray("required").add("filter").add("value");
        applyFilter.set("inputSchema", applyFilterSchema);

        ObjectNode runGridding = tools.addObject();
        runGridding.put("name", "run_gridding");
        runGridding.put("description", "Run gridding (spatial interpolation) for a data series of "
                + "an open data file. The resulting grid is shown as a map overlay. "
                + "May take a while on large files.");
        ObjectNode runGriddingSchema = objectSchema();
        addFileProperty(runGriddingSchema);
        addProperty(runGriddingSchema, "series", "string",
                "Series name; defaults to the series selected in the UI.");
        addProperty(runGriddingSchema, "cell_size", "number", "Grid cell size in meters.");
        addProperty(runGriddingSchema, "blanking_distance", "number",
                "Blanking distance in meters: cells farther than this from any data point are left empty.");
        runGriddingSchema.putArray("required").add("cell_size").add("blanking_distance");
        runGridding.set("inputSchema", runGriddingSchema);

        ObjectNode listLines = tools.addObject();
        listLines.put("name", "list_lines");
        listLines.put("description", "List survey lines of an open data file "
                + "with their point index ranges.");
        ObjectNode listLinesSchema = objectSchema();
        addFileProperty(listLinesSchema);
        listLines.set("inputSchema", listLinesSchema);

        ObjectNode splitLine = tools.addObject();
        splitLine.put("name", "split_line");
        splitLine.put("description", "Split (cut) a survey line into two at the given point index; "
                + "the point becomes the start of a new line. Supports undo.");
        ObjectNode splitLineSchema = objectSchema();
        addFileProperty(splitLineSchema);
        addProperty(splitLineSchema, "index", "integer", "Point index where the line is cut.");
        splitLineSchema.putArray("required").add("index");
        splitLine.set("inputSchema", splitLineSchema);

        ObjectNode mergeLines = tools.addObject();
        mergeLines.put("name", "merge_lines");
        mergeLines.put("description", "Merge a survey line with the following line. Supports undo.");
        ObjectNode mergeLinesSchema = objectSchema();
        addFileProperty(mergeLinesSchema);
        addProperty(mergeLinesSchema, "line", "integer",
                "Line index to merge with the following line, see list_lines.");
        mergeLinesSchema.putArray("required").add("line");
        mergeLines.set("inputSchema", mergeLinesSchema);

        ObjectNode deleteLine = tools.addObject();
        deleteLine.put("name", "delete_line");
        deleteLine.put("description", "Delete a survey line and all its points. Supports undo.");
        ObjectNode deleteLineSchema = objectSchema();
        addFileProperty(deleteLineSchema);
        addProperty(deleteLineSchema, "line", "integer", "Line index to delete, see list_lines.");
        deleteLineSchema.putArray("required").add("line");
        deleteLine.set("inputSchema", deleteLineSchema);

        ObjectNode cropByRegion = tools.addObject();
        cropByRegion.put("name", "crop_by_region");
        cropByRegion.put("description", "Crop an open data file by a geographic region: keeps only "
                + "the points inside the polygon and removes the rest. Line numbering is updated "
                + "to keep lines continuous. Supports undo.");
        ObjectNode cropByRegionSchema = objectSchema();
        addFileProperty(cropByRegionSchema);
        ObjectNode polygon = addProperty(cropByRegionSchema, "polygon", "array",
                "Polygon vertices as [latitude, longitude] pairs, at least 3.");
        ObjectNode vertex = polygon.putObject("items");
        vertex.put("type", "array");
        vertex.putObject("items").put("type", "number");
        cropByRegionSchema.putArray("required").add("polygon");
        cropByRegion.set("inputSchema", cropByRegionSchema);

        ObjectNode readData = tools.addObject();
        readData.put("name", "read_data");
        readData.put("description", "Read one or more data series aligned and decimated to at most "
                + "max_points buckets. Use for overview reads of large files instead of paging "
                + "read_series. Each bucket aggregates bucket_size consecutive values; bucket i "
                + "starts at point index start + i * bucket_size.");
        ObjectNode readDataSchema = objectSchema();
        addFileProperty(readDataSchema);
        ObjectNode readDataSeries = addProperty(readDataSchema, "series", "array", "Series names to read.");
        readDataSeries.putObject("items").put("type", "string");
        addProperty(readDataSchema, "start", "integer", "Index of the first point, default 0.");
        addProperty(readDataSchema, "count", "integer", "Number of points to cover, default all.");
        addProperty(readDataSchema, "max_points", "integer",
                "Maximum values per series, default 1000, maximum 10000.");
        ObjectNode aggregate = addProperty(readDataSchema, "aggregate", "string",
                "Bucket aggregate: mean (default), min, max or first.");
        aggregate.putArray("enum").add("mean").add("min").add("max").add("first");
        readDataSchema.putArray("required").add("series");
        readData.set("inputSchema", readDataSchema);

        ObjectNode seriesStats = tools.addObject();
        seriesStats.put("name", "get_series_stats");
        seriesStats.put("description", "Statistics of a data series: count, nulls, min, max, mean, "
                + "standard deviation and percentiles. Scope to a point range or a single line "
                + "with the optional arguments.");
        ObjectNode seriesStatsSchema = objectSchema();
        addFileProperty(seriesStatsSchema);
        addProperty(seriesStatsSchema, "series", "string", "Series (column) name.");
        addProperty(seriesStatsSchema, "start", "integer", "Index of the first point, default 0.");
        addProperty(seriesStatsSchema, "count", "integer", "Number of points, default all.");
        addProperty(seriesStatsSchema, "line", "integer",
                "Line index to scope the stats to, overrides start and count.");
        seriesStatsSchema.putArray("required").add("series");
        seriesStats.set("inputSchema", seriesStatsSchema);

        ObjectNode fileInfo = tools.addObject();
        fileInfo.put("name", "get_file_info");
        fileInfo.put("description", "Summary of an open data file: points, lines, series, time range, "
                + "sample rate and position update rate.");
        ObjectNode fileInfoSchema = objectSchema();
        addFileProperty(fileInfoSchema);
        fileInfo.set("inputSchema", fileInfoSchema);

        ObjectNode gridImage = tools.addObject();
        gridImage.put("name", "get_grid_image");
        gridImage.put("description", "Render the current grid of an open data file to a PNG image "
                + "covering the full grid extent. Requires run_gridding first.");
        ObjectNode gridImageSchema = objectSchema();
        addFileProperty(gridImageSchema);
        addProperty(gridImageSchema, "width", "integer", "Image width in pixels, default 800, maximum 2000.");
        ObjectNode gridBounds = addProperty(gridImageSchema, "bounds", "array",
                "Geographic region to render as [south, west, north, east] in degrees "
                + "(minimum latitude, minimum longitude, maximum latitude, maximum longitude); "
                + "zooms the image to that part of the grid. Default: the full grid extent.");
        gridBounds.putObject("items").put("type", "number");
        gridImage.set("inputSchema", gridImageSchema);

        ObjectNode chartImage = tools.addObject();
        chartImage.put("name", "get_chart_image");
        chartImage.put("description", "Screenshot of the chart of an open data file "
                + "as shown in the GeoHammer UI.");
        ObjectNode chartImageSchema = objectSchema();
        addFileProperty(chartImageSchema);
        chartImage.set("inputSchema", chartImageSchema);

        ObjectNode screenshot = tools.addObject();
        screenshot.put("name", "get_screenshot");
        screenshot.put("description", "Screenshot of the whole GeoHammer window: "
                + "map, charts and tool panels as the user sees them.");
        screenshot.set("inputSchema", objectSchema());

        ObjectNode undo = tools.addObject();
        undo.put("name", "undo");
        undo.put("description", "Undo the last data modification, same as the undo button in the UI.");
        undo.set("inputSchema", objectSchema());

        ObjectNode cutToLines = tools.addObject();
        cutToLines.put("name", "cut_to_lines");
        cutToLines.put("description", "Keep only the given point index ranges of an open data file; "
                + "each range becomes a survey line, all other points (turns, warm-up) are removed. "
                + "Single atomic operation with one undo step. Ranges are [from, to) pairs "
                + "in ascending order without overlaps.");
        ObjectNode cutToLinesSchema = objectSchema();
        addFileProperty(cutToLinesSchema);
        ObjectNode ranges = addProperty(cutToLinesSchema, "ranges", "array",
                "Point index ranges to keep as lines: [[from, to], ...], to is exclusive.");
        ObjectNode rangeItem = ranges.putObject("items");
        rangeItem.put("type", "array");
        rangeItem.putObject("items").put("type", "integer");
        cutToLinesSchema.putArray("required").add("ranges");
        cutToLines.set("inputSchema", cutToLinesSchema);

        ObjectNode gprInfo = tools.addObject();
        gprInfo.put("name", "get_gpr_info");
        gprInfo.put("description", "Summary of an open GPR (ground penetrating radar) file. "
                + "A GPR file is a sequence of traces; each trace is a column of amplitude samples "
                + "along the time/depth axis recorded at one surface position. Returns the number of "
                + "traces, samples per trace, sample interval, depth scale (centimeters per sample "
                + "in air and in ground, samples per meter), whether background noise was already "
                + "removed, line count and unsaved status.");
        ObjectNode gprInfoSchema = objectSchema();
        addFileProperty(gprInfoSchema);
        gprInfo.set("inputSchema", gprInfoSchema);

        ObjectNode readTraces = tools.addObject();
        readTraces.put("name", "read_traces");
        readTraces.put("description", "Read a window of GPR trace amplitudes as a 2D array, decimated "
                + "by averaging to at most max_traces x max_samples cells. Row i of the result covers "
                + "traces starting at trace_start + i * trace_bucket_size; column j covers samples "
                + "starting at sample_start + j * sample_bucket_size. Use get_gpr_info first "
                + "for the file dimensions.");
        ObjectNode readTracesSchema = objectSchema();
        addFileProperty(readTracesSchema);
        addProperty(readTracesSchema, "trace_start", "integer", "First trace index, 0-based, default 0.");
        addProperty(readTracesSchema, "trace_count", "integer", "Number of traces to cover, default all.");
        addProperty(readTracesSchema, "sample_start", "integer", "First sample index, 0-based, default 0.");
        addProperty(readTracesSchema, "sample_count", "integer", "Number of samples to cover, default all.");
        addProperty(readTracesSchema, "max_traces", "integer",
                "Maximum rows in the result, default 200, maximum 500.");
        addProperty(readTracesSchema, "max_samples", "integer",
                "Maximum columns in the result, default 200, maximum 500.");
        readTraces.set("inputSchema", readTracesSchema);

        ObjectNode removeBackground = tools.addObject();
        removeBackground.put("name", "remove_gpr_background");
        removeBackground.put("description", "Remove background noise from an open GPR file by "
                + "subtracting the average horizontal profile from all traces. Same as the "
                + "'Remove background' button in the UI. Supports undo.");
        ObjectNode removeBackgroundSchema = objectSchema();
        addFileProperty(removeBackgroundSchema);
        removeBackground.set("inputSchema", removeBackgroundSchema);

        ObjectNode cropSamples = tools.addObject();
        cropSamples.put("name", "crop_gpr_samples");
        cropSamples.put("description", "Crop the sample (time/depth) range of an open GPR file: keeps "
                + "samples [offset, offset + length) of every trace and discards the rest. "
                + "Use to cut away ringing at the top or noise below the depth of interest. "
                + "Supports undo.");
        ObjectNode cropSamplesSchema = objectSchema();
        addFileProperty(cropSamplesSchema);
        addProperty(cropSamplesSchema, "offset", "integer", "First sample index to keep, 0-based.");
        addProperty(cropSamplesSchema, "length", "integer", "Number of samples to keep.");
        cropSamplesSchema.putArray("required").add("offset").add("length");
        cropSamples.set("inputSchema", cropSamplesSchema);

        ObjectNode listScripts = tools.addObject();
        listScripts.put("name", "list_scripts");
        listScripts.put("description", "List processing scripts available in GeoHammer. Scripts are "
                + "Python programs stored in the GeoHammer scripts folder; they receive a copy of an "
                + "open file as CSV, may modify it (the result is loaded back, with undo) and may print "
                + "results to stdout. Each script declares typed parameters and the file templates it "
                + "applies to (matched against the file's template from list_files; an empty list means "
                + "any file). Run them with run_script, inspect code with get_script, create new ones "
                + "with create_script.");
        ObjectNode listScriptsSchema = objectSchema();
        listScripts.set("inputSchema", listScriptsSchema);

        ObjectNode getScript = tools.addObject();
        getScript.put("name", "get_script");
        getScript.put("description", "Read the Python source code and metadata of a script, "
                + "see list_scripts for available scripts.");
        ObjectNode getScriptSchema = objectSchema();
        addProperty(getScriptSchema, "script", "string",
                "Script file name from list_scripts, with or without the .py extension.");
        getScriptSchema.putArray("required").add("script");
        getScript.set("inputSchema", getScriptSchema);

        ObjectNode runScript = tools.addObject();
        runScript.put("name", "run_script");
        runScript.put("description", "Run a processing script on an open file and wait for completion. "
                + "The file is exported to a temporary CSV, the script runs on it with the given "
                + "parameters, the modified file is loaded back (undoable, the file on disk is not "
                + "changed) and the captured script output (stdout) is returned. "
                + "May take minutes on large files; missing Python dependencies are installed "
                + "automatically on first use.");
        ObjectNode runScriptSchema = objectSchema();
        addProperty(runScriptSchema, "script", "string",
                "Script file name from list_scripts, with or without the .py extension.");
        addFileProperty(runScriptSchema);
        ObjectNode scriptParams = addProperty(runScriptSchema, "params", "object",
                "Script parameters as name-value pairs; see the script's parameter list "
                + "in list_scripts for names, types and which are required.");
        scriptParams.putObject("additionalProperties");
        runScriptSchema.putArray("required").add("script");
        runScript.set("inputSchema", runScriptSchema);

        ObjectNode createScript = tools.addObject();
        createScript.put("name", "create_script");
        createScript.put("description", "Create (or overwrite) a reusable processing script in the "
                + "GeoHammer scripts folder, making it available to run_script and to the Scripts tool "
                + "in the UI. The script must be a Python program that takes the path of a CSV file as "
                + "its first positional argument, plus the declared parameters as --name value options "
                + "(boolean parameters are passed as a flag without a value when true). To return "
                + "results without changing data, print to stdout and leave the file unmodified. "
                + "Common libraries (pandas, numpy, scipy) may be imported; missing ones are installed "
                + "on first run.");
        ObjectNode createScriptSchema = objectSchema();
        addProperty(createScriptSchema, "name", "string",
                "Script file name without extension; letters, digits, underscore and dash only.");
        addProperty(createScriptSchema, "display_name", "string",
                "Human-readable name shown in the UI; defaults to the file name.");
        addProperty(createScriptSchema, "code", "string", "Python source code of the script.");
        ObjectNode createParams = addProperty(createScriptSchema, "parameters", "array",
                "Declared script parameters. Each item: {name, display_name?, type "
                + "(STRING | INTEGER | DOUBLE | BOOLEAN | FILE_PATH | FOLDER_PATH | COLUMN_NAME | ENUM), "
                + "default_value?, required?, enum_values? (for ENUM), min?, max? (for numbers)}.");
        createParams.putObject("items").put("type", "object");
        ObjectNode createTemplates = addProperty(createScriptSchema, "templates", "array",
                "File templates the script applies to (template values from list_files); "
                + "empty or omitted means any file.");
        createTemplates.putObject("items").put("type", "string");
        addProperty(createScriptSchema, "overwrite", "boolean",
                "Set true to replace an existing script with the same name, default false.");
        createScriptSchema.putArray("required").add("name").add("code");
        createScript.set("inputSchema", createScriptSchema);

        ObjectNode pythonPath = tools.addObject();
        pythonPath.put("name", "set_python_path");
        pythonPath.put("description", "Get or set the Python interpreter used to run scripts. "
                + "Call without arguments to report the current interpreter path and version; "
                + "pass path to switch to a different interpreter (Python 3.8 or newer). "
                + "The setting persists in the application preferences.");
        ObjectNode pythonPathSchema = objectSchema();
        addProperty(pythonPathSchema, "path", "string",
                "Full path of the python executable; omit to only report the current one.");
        pythonPath.set("inputSchema", pythonPathSchema);

        return tools;
    }

    public ObjectNode callTool(JsonNode params) {
        String name = params.path("name").asText(Strings.empty());
        JsonNode args = params.path("arguments");
        try {
            Object result = switch (name) {
                case "list_files" -> listFiles();
                case "list_series" -> listSeries(args);
                case "read_series" -> readSeries(args);
                case "write_series" -> writeSeries(args);
                case "place_marks" -> placeMarks(args);
                case "clear_marks" -> clearMarks(args);
                case "select_file" -> selectFile(args);
                case "select_series" -> selectSeries(args);
                case "create_series" -> createSeries(args);
                case "remove_series" -> removeSeries(args);
                case "apply_filter" -> applyFilter(args);
                case "run_gridding" -> runGridding(args);
                case "list_lines" -> listLines(args);
                case "split_line" -> splitLine(args);
                case "merge_lines" -> mergeLines(args);
                case "delete_line" -> deleteLine(args);
                case "crop_by_region" -> cropByRegion(args);
                case "read_data" -> readData(args);
                case "get_series_stats" -> getSeriesStats(args);
                case "get_file_info" -> getFileInfo(args);
                case "get_grid_image" -> getGridImage(args);
                case "get_chart_image" -> getChartImage(args);
                case "get_screenshot" -> getScreenshot();
                case "undo" -> undo();
                case "cut_to_lines" -> cutToLines(args);
                case "get_gpr_info" -> getGprInfo(args);
                case "read_traces" -> readTraces(args);
                case "remove_gpr_background" -> removeGprBackground(args);
                case "crop_gpr_samples" -> cropGprSamples(args);
                case "list_scripts" -> listScripts();
                case "get_script" -> getScript(args);
                case "run_script" -> runScript(args);
                case "create_script" -> createScript(args);
                case "set_python_path" -> setPythonPath(args);
                default -> throw new IllegalArgumentException("Unknown tool: " + name);
            };
            // image tools build the full tool result themselves
            return result instanceof ObjectNode node ? node : toolResult((String) result, false);
        } catch (IllegalArgumentException e) {
            // invalid tool arguments, report back to the client
            return toolResult(e.getMessage(), true);
        } catch (Exception e) {
            log.error("MCP tool call failed: " + name, e);
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            return toolResult(message, true);
        }
    }

    private String listFiles() throws Exception {
        return inFxThread(() -> {
            ArrayNode files = mapper.createArrayNode();
            for (SgyFile dataFile : dataFiles()) {
                File file = dataFile.getFile();
                ObjectNode node = files.addObject();
                node.put("name", file != null ? file.getName() : null);
                node.put("path", file != null ? file.getAbsolutePath() : null);
                node.put("kind", fileKind(dataFile));
                node.put("template", Templates.getTemplateName(dataFile));
                node.put("points", dataFile.numTraces());
                node.put("unsaved", dataFile.isUnsaved());
            }
            return toJson(files);
        });
    }

    private String listSeries(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            ArrayNode series = mapper.createArrayNode();
            ColumnSchema schema = GeoData.getSchema(dataFile.getGeoData());
            if (schema != null) {
                for (Column column : schema) {
                    ObjectNode node = series.addObject();
                    node.put("name", column.getHeader());
                    if (!Strings.isNullOrEmpty(column.getSemantic())) {
                        node.put("semantic", column.getSemantic());
                    }
                    if (!Strings.isNullOrEmpty(column.getUnit())) {
                        node.put("unit", column.getUnit());
                    }
                    node.put("visible", column.isDisplay());
                    node.put("readOnly", column.isReadOnly());
                }
            }
            return toJson(series);
        });
    }

    private String readSeries(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        int start = args.path("start").asInt(0);
        int count = args.path("count").asInt(DEFAULT_READ_COUNT);
        if (start < 0 || count < 0) {
            throw new IllegalArgumentException("start and count must be non-negative");
        }
        int limit = Math.min(count, MAX_READ_COUNT);
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            getColumn(dataFile, seriesName);

            int total = geoData.size();
            int from = Math.min(start, total);
            int to = Math.min(from + limit, total);

            ObjectNode result = mapper.createObjectNode();
            File file = dataFile.getFile();
            result.put("file", file != null ? file.getName() : null);
            result.put("series", seriesName);
            result.put("total", total);
            result.put("start", from);
            result.put("count", to - from);
            ArrayNode values = result.putArray("values");
            for (int i = from; i < to; i++) {
                Object value = geoData.get(i).getValue(seriesName);
                if (value instanceof Number number) {
                    values.add(number.doubleValue());
                } else if (value instanceof String string) {
                    values.add(string);
                } else {
                    values.addNull();
                }
            }
            return toJson(result);
        });
    }

    private String writeSeries(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        int start = args.path("start").asInt(0);
        JsonNode valuesNode = args.path("values");
        if (!valuesNode.isArray() || valuesNode.isEmpty()) {
            throw new IllegalArgumentException("values must be a non-empty array of numbers or nulls");
        }
        List<Object> values = parseValues(valuesNode);
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            Column column = getColumn(dataFile, seriesName);
            if (column.isReadOnly()) {
                throw new IllegalArgumentException("Series is read-only: " + seriesName);
            }
            if (start < 0 || start + values.size() > geoData.size()) {
                throw new IllegalArgumentException("Value range [" + start + ", "
                        + (start + values.size()) + ") is out of bounds, file has "
                        + geoData.size() + " points");
            }

            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();
            for (int i = 0; i < values.size(); i++) {
                geoData.get(start + i).setValue(seriesName, values.get(i));
            }
            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            dataFile.setUnsaved(true);
            model.reload(dataFile);

            return "Wrote " + values.size() + " values to series " + seriesName
                    + " starting at index " + start;
        });
    }

    private String placeMarks(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        List<Integer> indices = readIndices(args);
        if (indices == null || indices.isEmpty()) {
            throw new IllegalArgumentException("indices must be a non-empty array of point indices");
        }
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            int numPoints = dataFile.numTraces();
            for (int index : indices) {
                if (index < 0 || index >= numPoints) {
                    throw new IllegalArgumentException("Point index out of bounds: " + index
                            + ", file has " + numPoints + " points");
                }
            }
            Set<Integer> markedIndices = new HashSet<>();
            for (BaseObject element : dataFile.getAuxElements()) {
                if (element instanceof FoundPlace flag) {
                    markedIndices.add(flag.getTraceIndex());
                }
            }
            Chart chart = model.getChart(dataFile);
            int placed = 0;
            for (int index : indices) {
                if (!markedIndices.add(index)) {
                    continue;
                }
                FoundPlace flag = new FoundPlace(new TraceKey(dataFile, index), model);
                dataFile.getAuxElements().add(flag);
                if (chart != null) {
                    chart.addFlag(flag);
                }
                placed++;
            }
            dataFile.setUnsaved(true);
            model.updateAuxElements();
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
            return "Placed " + placed + " marks, " + (indices.size() - placed) + " skipped as duplicates";
        });
    }

    private String clearMarks(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        List<Integer> indices = readIndices(args);
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            Chart chart = model.getChart(dataFile);
            int removed = 0;
            Iterator<BaseObject> it = dataFile.getAuxElements().iterator();
            while (it.hasNext()) {
                if (it.next() instanceof FoundPlace flag
                        && (indices == null || indices.contains(flag.getTraceIndex()))) {
                    it.remove();
                    if (chart != null) {
                        chart.removeFlag(flag);
                    }
                    removed++;
                }
            }
            if (removed > 0) {
                dataFile.setUnsaved(true);
                model.updateAuxElements();
                model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
            }
            return "Removed " + removed + " marks";
        });
    }

    private String selectFile(JsonNode args) throws Exception {
        String fileName = requiredString(args, "file");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            Chart chart = model.getChart(dataFile);
            if (chart == null) {
                throw new IllegalArgumentException("File has no chart");
            }
            model.selectAndScrollToChart(chart);
            File file = dataFile.getFile();
            return "Selected file " + (file != null ? file.getName() : "");
        });
    }

    private String removeSeries(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            SensorLineChart chart = getSensorChart(dataFile);
            Column column = getColumn(dataFile, seriesName);
            if (column.isReadOnly()) {
                throw new IllegalArgumentException("Series is read-only and cannot be deleted: "
                        + seriesName);
            }
            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();
            chart.removeFileColumn(seriesName);
            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            return "Deleted series " + seriesName;
        });
    }

    private String selectSeries(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            SensorLineChart chart = getSensorChart(dataFile);
            if (!chart.getSeriesNames().contains(seriesName)) {
                throw new IllegalArgumentException("Series has no chart: " + seriesName
                        + "; series with charts: " + String.join(", ", chart.getSeriesNames()));
            }
            model.selectAndScrollToChart(chart);
            chart.selectChart(seriesName);
            return "Selected series " + seriesName;
        });
    }

    private String createSeries(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        String unit = optionalString(args, "unit");
        JsonNode valuesNode = args.get("values");
        List<Object> values = valuesNode != null && valuesNode.isArray()
                ? parseValues(valuesNode)
                : List.of();
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            ColumnSchema schema = GeoData.getSchema(geoData);
            if (schema == null) {
                throw new IllegalArgumentException("File has no data");
            }
            if (schema.getColumn(seriesName) != null) {
                throw new IllegalArgumentException("Series already exists: " + seriesName);
            }
            if (values.size() > geoData.size()) {
                throw new IllegalArgumentException("Too many values: " + values.size()
                        + ", file has " + geoData.size() + " points");
            }

            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();
            Column column = GeoData.addColumn(geoData, new Column(seriesName));
            column.setDisplay(true);
            if (!Strings.isNullOrEmpty(unit)) {
                column.setUnit(unit);
            }
            for (int i = 0; i < values.size(); i++) {
                geoData.get(i).setValue(seriesName, values.get(i));
            }
            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            dataFile.setUnsaved(true);
            model.reload(dataFile);

            return "Created series " + seriesName
                    + (values.isEmpty() ? "" : " with " + values.size() + " initial values");
        });
    }

    private String applyFilter(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String filter = requiredString(args, "filter");
        String seriesArg = optionalString(args, "series");
        JsonNode valueNode = args.get("value");
        if (valueNode == null || !valueNode.canConvertToInt()) {
            throw new IllegalArgumentException("value must be an integer");
        }
        int value = valueNode.asInt();

        FilterTarget target = inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            SensorLineChart chart = getSensorChart(dataFile);
            String series = !Strings.isNullOrEmpty(seriesArg)
                    ? seriesArg
                    : chart.getSelectedSeriesName();
            if (Strings.isNullOrEmpty(series)) {
                throw new IllegalArgumentException("No series selected, specify a series name");
            }
            if (!chart.getSeriesNames().contains(series)) {
                throw new IllegalArgumentException("Series has no chart: " + series
                        + "; series with charts: " + String.join(", ", chart.getSeriesNames()));
            }
            return new FilterTarget(chart, series);
        });

        // filters run on a background thread, as filter tools do
        String suffix = switch (filter) {
            case "lowpass" -> {
                if (value < 0) {
                    throw new IllegalArgumentException("Filter length must be non-negative");
                }
                target.chart().applyLowPass(target.series(), value);
                yield "_LPF";
            }
            case "timelag" -> {
                target.chart().applyTimeLag(target.series(), value);
                yield "_LAG";
            }
            case "running_median" -> {
                if (value < 1) {
                    throw new IllegalArgumentException("Window size must be positive");
                }
                target.chart().applyRunningMedian(target.series(), value);
                yield "_RM";
            }
            default -> throw new IllegalArgumentException("Unknown filter: " + filter
                    + "; supported: lowpass, timelag, running_median");
        };

        String baseName = target.series().endsWith(suffix)
                ? target.series().substring(0, target.series().length() - suffix.length())
                : target.series();
        return "Applied " + filter + " to series " + target.series()
                + ", result written to series " + baseName + suffix;
    }

    private String runGridding(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesArg = optionalString(args, "series");
        double cellSize = args.path("cell_size").asDouble(0);
        double blankingDistance = args.path("blanking_distance").asDouble(0);
        if (cellSize <= 0 || blankingDistance <= 0) {
            throw new IllegalArgumentException("cell_size and blanking_distance must be positive");
        }

        GridTarget target = inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            String series = !Strings.isNullOrEmpty(seriesArg)
                    ? seriesArg
                    : model.getSelectedSeriesName(dataFile);
            if (Strings.isNullOrEmpty(series)) {
                throw new IllegalArgumentException("No series selected, specify a series name");
            }
            getColumn(dataFile, series);
            return new GridTarget(dataFile, series);
        });

        // set a default display filter so that the grid gets rendered
        if (gridLayer.getFilter(target.file(), target.series()) == null) {
            Range range = getSeriesRange(target.file(), target.series());
            gridLayer.setFilter(target.file(), target.series(), new GriddingFilter(
                    range, false, false, false,
                    PaletteType.defaultPaletteType(), SpectrumType.defaultSpectrumType()));
        }

        // gridding runs on a background thread, as the gridding tool does
        GriddingParams params = new GriddingParams(cellSize, blankingDistance);
        GriddingResult result = griddingService.runGridding(
                List.of(target.file()), target.series(), params);
        if (result == null) {
            throw new IllegalArgumentException("Gridding produced no grid: the series has no values "
                    + "or the data extent is smaller than the cell size");
        }
        gridLayer.setResult(target.file(), result);

        float[][] grid = result.grid();
        int width = grid.length;
        int height = width > 0 ? grid[0].length : 0;
        return "Gridding completed for series " + target.series()
                + ", grid size " + width + " x " + height + " cells";
    }

    private String listLines(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            ArrayNode lines = mapper.createArrayNode();
            for (Map.Entry<Integer, IndexRange> entry : dataFile.getLineRanges().entrySet()) {
                IndexRange range = entry.getValue();
                ObjectNode node = lines.addObject();
                node.put("line", entry.getKey());
                node.put("from", range.from());
                node.put("to", range.to());
                node.put("points", range.to() - range.from());
            }
            return toJson(lines);
        });
    }

    private String splitLine(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int index = args.path("index").asInt(-1);
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            if (index < 0 || index >= dataFile.numTraces()) {
                throw new IllegalArgumentException("Point index out of bounds: " + index
                        + ", file has " + dataFile.numTraces() + " points");
            }
            if (traceTransform.isStartOfLine(dataFile, index)) {
                throw new IllegalArgumentException("Point " + index + " is already a start of a line");
            }
            traceTransform.splitLine(dataFile, index);
            return "Split line at point " + index;
        });
    }

    private String mergeLines(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int line = args.path("line").asInt(-1);
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            IndexRange range = getLineRange(dataFile, line);
            if (!traceTransform.hasNextLine(dataFile, range.from())) {
                throw new IllegalArgumentException("Line " + line + " has no following line to merge with");
            }
            traceTransform.mergeLineWithNext(dataFile, range.from());
            return "Merged line " + line + " with the following line";
        });
    }

    private String deleteLine(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int line = args.path("line").asInt(-1);
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            IndexRange range = getLineRange(dataFile, line);
            int points = range.to() - range.from();
            traceTransform.removeLine(dataFile, line);
            return "Deleted line " + line + " with " + points + " points";
        });
    }

    private String cropByRegion(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        JsonNode polygonNode = args.get("polygon");
        if (polygonNode == null || !polygonNode.isArray() || polygonNode.size() < 3) {
            throw new IllegalArgumentException("polygon must be an array of at least 3 [latitude, longitude] pairs");
        }
        List<LatLon> polygon = new ArrayList<>(polygonNode.size());
        for (JsonNode vertexNode : polygonNode) {
            if (!vertexNode.isArray() || vertexNode.size() != 2
                    || !vertexNode.get(0).isNumber() || !vertexNode.get(1).isNumber()) {
                throw new IllegalArgumentException("Each polygon vertex must be a [latitude, longitude] pair");
            }
            polygon.add(new LatLon(vertexNode.get(0).asDouble(), vertexNode.get(1).asDouble()));
        }
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            int before = dataFile.numTraces();

            // project polygon to screen coordinates at a fixed zoom,
            // same as the trace cutter does
            MapField field = new MapField(model.getMapField());
            field.setZoom(28);
            List<Point2D> area = new ArrayList<>(polygon.size());
            for (LatLon vertex : polygon) {
                area.add(field.latLonToScreen(vertex));
            }

            traceTransform.cropLines(List.of(dataFile), field, area);

            int after = dataFile.numTraces();
            if (after == before) {
                return before == 0
                        ? "File has no points"
                        : "File not changed: all points are inside the region or none are";
            }
            return "Kept " + after + " points inside the region, removed " + (before - after);
        });
    }

    private String readData(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        JsonNode seriesNode = args.get("series");
        if (seriesNode == null || !seriesNode.isArray() || seriesNode.isEmpty()) {
            throw new IllegalArgumentException("series must be a non-empty array of series names");
        }
        List<String> seriesNames = new ArrayList<>(seriesNode.size());
        for (JsonNode s : seriesNode) {
            if (!s.isTextual()) {
                throw new IllegalArgumentException("series must be an array of series names");
            }
            seriesNames.add(s.asText());
        }
        int start = args.path("start").asInt(0);
        int count = args.path("count").asInt(Integer.MAX_VALUE);
        int maxPoints = Math.min(args.path("max_points").asInt(1000), MAX_READ_COUNT);
        if (start < 0 || count < 0 || maxPoints < 1) {
            throw new IllegalArgumentException("start, count and max_points must be non-negative");
        }
        String aggregate = args.path("aggregate").asText("mean");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            for (String seriesName : seriesNames) {
                getColumn(dataFile, seriesName);
            }
            int total = geoData.size();
            int from = Math.min(start, total);
            int to = Math.min(from + count, total);
            int span = to - from;
            int bucketSize = Math.max(1, (int) Math.ceil((double) span / maxPoints));
            int numBuckets = (span + bucketSize - 1) / bucketSize;

            ObjectNode result = mapper.createObjectNode();
            File file = dataFile.getFile();
            result.put("file", file != null ? file.getName() : null);
            result.put("total", total);
            result.put("start", from);
            result.put("count", span);
            result.put("bucket_size", bucketSize);
            result.put("aggregate", aggregate);
            ObjectNode values = result.putObject("values");
            for (String seriesName : seriesNames) {
                ArrayNode array = values.putArray(seriesName);
                for (int b = 0; b < numBuckets; b++) {
                    int bFrom = from + b * bucketSize;
                    int bTo = Math.min(bFrom + bucketSize, to);
                    double acc = 0;
                    int n = 0;
                    for (int i = bFrom; i < bTo; i++) {
                        if (geoData.get(i).getNumber(seriesName) instanceof Number number) {
                            double v = number.doubleValue();
                            n++;
                            switch (aggregate) {
                                case "min" -> acc = n == 1 ? v : Math.min(acc, v);
                                case "max" -> acc = n == 1 ? v : Math.max(acc, v);
                                case "first" -> acc = n == 1 ? v : acc;
                                default -> acc += v;
                            }
                        }
                    }
                    if (n == 0) {
                        array.addNull();
                    } else {
                        array.add("mean".equals(aggregate) ? acc / n : acc);
                    }
                }
            }
            return toJson(result);
        });
    }

    private String getSeriesStats(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String seriesName = requiredString(args, "series");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            getColumn(dataFile, seriesName);
            List<GeoData> geoData = dataFile.getGeoData();
            int from;
            int to;
            if (args.has("line")) {
                IndexRange range = getLineRange(dataFile, args.path("line").asInt(-1));
                from = range.from();
                to = range.to();
            } else {
                from = Math.max(0, args.path("start").asInt(0));
                from = Math.min(from, geoData.size());
                to = Math.min(from + args.path("count").asInt(Integer.MAX_VALUE), geoData.size());
            }
            List<Double> values = new ArrayList<>(to - from);
            double sum = 0;
            for (int i = from; i < to; i++) {
                Number number = geoData.get(i).getNumber(seriesName);
                if (number != null) {
                    double v = number.doubleValue();
                    values.add(v);
                    sum += v;
                }
            }
            ObjectNode result = mapper.createObjectNode();
            result.put("series", seriesName);
            result.put("start", from);
            result.put("count", to - from);
            result.put("nulls", to - from - values.size());
            if (values.isEmpty()) {
                return toJson(result);
            }
            values.sort(null);
            int n = values.size();
            double mean = sum / n;
            double variance = 0;
            for (double v : values) {
                variance += (v - mean) * (v - mean);
            }
            result.put("min", values.getFirst());
            result.put("max", values.getLast());
            result.put("mean", mean);
            result.put("std", Math.sqrt(variance / n));
            result.put("median", values.get(n / 2));
            ObjectNode percentiles = result.putObject("percentiles");
            for (int p : new int[] {1, 5, 25, 75, 95, 99}) {
                percentiles.put("p" + p, values.get(Math.min(n - 1, (int) (p / 100.0 * n))));
            }
            return toJson(result);
        });
    }

    private String getFileInfo(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> geoData = dataFile.getGeoData();
            ObjectNode result = mapper.createObjectNode();
            File file = dataFile.getFile();
            result.put("name", file != null ? file.getName() : null);
            result.put("path", file != null ? file.getAbsolutePath() : null);
            result.put("kind", fileKind(dataFile));
            result.put("template", Templates.getTemplateName(dataFile));
            result.put("points", geoData.size());
            result.put("unsaved", dataFile.isUnsaved());
            result.put("lines", dataFile.getLineRanges().size());
            ColumnSchema schema = GeoData.getSchema(geoData);
            result.put("series", schema != null ? schema.numColumns() : 0);

            Long first = null;
            Long last = null;
            int positionUpdates = 0;
            Double prevLat = null;
            Double prevLon = null;
            for (GeoData value : geoData) {
                Long timestamp = value.getTimestamp();
                if (timestamp != null) {
                    if (first == null) {
                        first = timestamp;
                    }
                    last = timestamp;
                }
                Double lat = value.getLatitude();
                Double lon = value.getLongitude();
                if (lat != null && lon != null
                        && (!lat.equals(prevLat) || !lon.equals(prevLon))) {
                    positionUpdates++;
                    prevLat = lat;
                    prevLon = lon;
                }
            }
            if (first != null && last > first) {
                double duration = (last - first) / 1000.0;
                result.put("startTime", Instant.ofEpochMilli(first).toString());
                result.put("endTime", Instant.ofEpochMilli(last).toString());
                result.put("durationSeconds", Math.round(duration * 1000.0) / 1000.0);
                result.put("sampleRateHz", Math.round((geoData.size() - 1) / duration * 10.0) / 10.0);
                if (positionUpdates > 1) {
                    result.put("positionUpdateRateHz",
                            Math.round((positionUpdates - 1) / duration * 10.0) / 10.0);
                    result.put("samplesPerPositionUpdate",
                            Math.round((double) geoData.size() / positionUpdates));
                }
            }
            return toJson(result);
        });
    }

    private ObjectNode getGridImage(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int width = Math.clamp(args.path("width").asInt(800), 200, 2000);
        SgyFile dataFile = inFxThread(() -> resolveFile(fileName));
        GriddingResult result = gridLayer.getResult(dataFile);
        if (result == null) {
            throw new IllegalArgumentException("File has no grid, run run_gridding first");
        }
        // the displayable grid is built asynchronously after gridding
        for (int i = 0; i < 50 && gridLayer.getGrid(dataFile) == null; i++) {
            Thread.sleep(200);
        }
        if (gridLayer.getGrid(dataFile) == null) {
            throw new IllegalStateException("Grid is not ready yet, retry in a moment");
        }

        LatLon min = result.minLatLon();
        LatLon max = result.maxLatLon();
        JsonNode boundsNode = args.get("bounds");
        if (boundsNode != null && !boundsNode.isNull()) {
            if (!boundsNode.isArray() || boundsNode.size() != 4) {
                throw new IllegalArgumentException(
                        "bounds must be [south, west, north, east] in degrees");
            }
            double south = Math.max(boundsNode.get(0).asDouble(), min.getLatDgr());
            double west = Math.max(boundsNode.get(1).asDouble(), min.getLonDgr());
            double north = Math.min(boundsNode.get(2).asDouble(), max.getLatDgr());
            double east = Math.min(boundsNode.get(3).asDouble(), max.getLonDgr());
            if (south >= north || west >= east) {
                throw new IllegalArgumentException("bounds do not intersect the grid extent: "
                        + "latitude " + min.getLatDgr() + ".." + max.getLatDgr()
                        + ", longitude " + min.getLonDgr() + ".." + max.getLonDgr());
            }
            min = new LatLon(south, west);
            max = new LatLon(north, east);
        }
        LatLon center = new LatLon(
                0.5 * (min.getLatDgr() + max.getLatDgr()),
                0.5 * (min.getLonDgr() + max.getLonDgr()));

        MapField field = new MapField();
        field.setPathCenter(center);
        field.setPathEdgeLL(
                new LatLon(max.getLatDgr(), min.getLonDgr()),
                new LatLon(min.getLatDgr(), max.getLonDgr()));
        field.setSceneCenter(center);

        // extent aspect ratio in meters
        double widthMeters = new LatLon(center.getLatDgr(), min.getLonDgr())
                .getDistance(new LatLon(center.getLatDgr(), max.getLonDgr()));
        double heightMeters = new LatLon(min.getLatDgr(), center.getLonDgr())
                .getDistance(new LatLon(max.getLatDgr(), center.getLonDgr()));
        int height = Math.clamp((int) Math.round(width * heightMeters / Math.max(1e-6, widthMeters)),
                100, 2000);
        field.adjustZoom((int) (width * 0.95), (int) (height * 0.95));

        // crop to the actual extent at the selected zoom
        Point2D corner = field.latLonToScreen(field.getPathRightBottom());
        int margin = 10;
        int imageWidth = Math.clamp(2 * (int) Math.abs(corner.getX()) + 2 * margin, 100, 2000);
        int imageHeight = Math.clamp(2 * (int) Math.abs(corner.getY()) + 2 * margin, 100, 2000);

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.translate(imageWidth / 2, imageHeight / 2);
            gridLayer.drawOnMapField(g2, field);
        } finally {
            g2.dispose();
        }
        String caption = "Grid of series " + result.seriesName()
                + ", extent %.1f x %.1f m, cell size %.2f m".formatted(
                        widthMeters, heightMeters, result.params().cellSize());
        return imageResult(toPng(image), caption);
    }

    private ObjectNode getChartImage(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            Chart chart = model.getChart(dataFile);
            if (chart == null) {
                throw new IllegalArgumentException("File has no chart");
            }
            File file = dataFile.getFile();
            String caption = "Chart of " + (file != null ? file.getName() : "file");
            return imageResult(snapshotNode(chart.getRootNode()), caption);
        });
    }

    private ObjectNode getScreenshot() throws Exception {
        return inFxThread(() -> {
            if (AppContext.stage == null || AppContext.stage.getScene() == null) {
                throw new IllegalStateException("Application window is not available");
            }
            Node root = AppContext.stage.getScene().getRoot();
            return imageResult(snapshotNode(root), "GeoHammer window");
        });
    }

    private String undo() throws Exception {
        return inFxThread(() -> {
            if (!undoModel.canUndo()) {
                return "Nothing to undo";
            }
            undoModel.undo();
            return undoModel.canUndo()
                    ? "Undone last operation"
                    : "Undone last operation, undo stack is now empty";
        });
    }

    private String cutToLines(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        JsonNode rangesNode = args.get("ranges");
        if (rangesNode == null || !rangesNode.isArray() || rangesNode.isEmpty()) {
            throw new IllegalArgumentException("ranges must be a non-empty array of [from, to] pairs");
        }
        List<int[]> ranges = new ArrayList<>(rangesNode.size());
        for (JsonNode rangeNode : rangesNode) {
            if (!rangeNode.isArray() || rangeNode.size() != 2
                    || !rangeNode.get(0).canConvertToInt() || !rangeNode.get(1).canConvertToInt()) {
                throw new IllegalArgumentException("Each range must be a [from, to] pair of point indices");
            }
            ranges.add(new int[] {rangeNode.get(0).asInt(), rangeNode.get(1).asInt()});
        }
        for (int i = 0; i < ranges.size(); i++) {
            int[] range = ranges.get(i);
            if (range[0] < 0 || range[1] <= range[0]) {
                throw new IllegalArgumentException("Invalid range: [" + range[0] + ", " + range[1] + ")");
            }
            if (i > 0 && range[0] < ranges.get(i - 1)[1]) {
                throw new IllegalArgumentException("Ranges must be ascending and not overlap");
            }
        }
        return inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            List<GeoData> values = dataFile.getGeoData();
            int total = values.size();
            if (ranges.getLast()[1] > total) {
                throw new IllegalArgumentException("Range end " + ranges.getLast()[1]
                        + " is out of bounds, file has " + total + " points");
            }
            ColumnSchema schema = GeoData.getSchema(values);
            if (schema == null || schema.getHeaderBySemantic(Semantic.LINE.getName()) == null) {
                throw new IllegalArgumentException("File has no line column");
            }

            FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();

            int[] newIndex = new int[total];
            Arrays.fill(newIndex, -1);
            List<GeoData> kept = new ArrayList<>();
            int lineIndex = 0;
            for (int[] range : ranges) {
                for (int i = range[0]; i < range[1]; i++) {
                    GeoData value = values.get(i);
                    value.setLine(lineIndex);
                    newIndex[i] = kept.size();
                    kept.add(value);
                }
                lineIndex++;
            }

            // reindex positional elements, drop the ones in removed regions
            Iterator<BaseObject> it = dataFile.getAuxElements().iterator();
            while (it.hasNext()) {
                if (it.next() instanceof PositionalObject positional) {
                    int oldIndex = positional.getTraceIndex();
                    int updated = oldIndex >= 0 && oldIndex < total ? newIndex[oldIndex] : -1;
                    if (updated == -1) {
                        it.remove();
                    } else {
                        positional.offset(updated - oldIndex);
                    }
                }
            }

            values.clear();
            values.addAll(kept);

            if (snapshot != null) {
                undoModel.push(new UndoFrame(snapshot));
            }
            dataFile.setUnsaved(true);
            model.reload(dataFile);

            return "Kept " + kept.size() + " points in " + ranges.size() + " lines, removed "
                    + (total - kept.size()) + " points";
        });
    }

    private String getGprInfo(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return inFxThread(() -> {
            TraceFile traceFile = resolveGprFile(fileName);
            ObjectNode result = mapper.createObjectNode();
            File file = traceFile.getFile();
            result.put("name", file != null ? file.getName() : null);
            result.put("template", Templates.getTemplateName(traceFile));
            result.put("traces", traceFile.numTraces());
            result.put("samplesPerTrace", traceFile.getMaxSamples());
            result.put("sampleInterval", traceFile.getSampleInterval());
            result.put("cmPerSampleInAir", traceFile.getSamplesToCmAir());
            result.put("cmPerSampleInGround", traceFile.getSamplesToCmGrn());
            result.put("samplesPerMeter", traceFile.getSamplesPerMeter());
            result.put("backgroundRemoved", traceFile.isBackgroundRemoved());
            result.put("lines", traceFile.getLineRanges().size());
            result.put("unsaved", traceFile.isUnsaved());
            return toJson(result);
        });
    }

    private String readTraces(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int traceStart = args.path("trace_start").asInt(0);
        int traceCount = args.path("trace_count").asInt(Integer.MAX_VALUE);
        int sampleStart = args.path("sample_start").asInt(0);
        int sampleCount = args.path("sample_count").asInt(Integer.MAX_VALUE);
        int maxTraces = Math.clamp(args.path("max_traces").asInt(200), 1, 500);
        int maxSamples = Math.clamp(args.path("max_samples").asInt(200), 1, 500);
        if (traceStart < 0 || traceCount < 0 || sampleStart < 0 || sampleCount < 0) {
            throw new IllegalArgumentException("Trace and sample ranges must be non-negative");
        }
        return inFxThread(() -> {
            TraceFile traceFile = resolveGprFile(fileName);
            List<Trace> traces = traceFile.getTraces();
            int numTraces = traces.size();
            int numSamples = traceFile.getMaxSamples();

            int traceFrom = Math.min(traceStart, numTraces);
            int traceTo = Math.min(traceFrom + traceCount, numTraces);
            int sampleFrom = Math.min(sampleStart, numSamples);
            int sampleTo = Math.min(sampleFrom + sampleCount, numSamples);

            int traceBucket = Math.max(1, (int) Math.ceil((double) (traceTo - traceFrom) / maxTraces));
            int sampleBucket = Math.max(1, (int) Math.ceil((double) (sampleTo - sampleFrom) / maxSamples));

            ObjectNode result = mapper.createObjectNode();
            result.put("traces", numTraces);
            result.put("samplesPerTrace", numSamples);
            result.put("trace_start", traceFrom);
            result.put("trace_count", traceTo - traceFrom);
            result.put("sample_start", sampleFrom);
            result.put("sample_count", sampleTo - sampleFrom);
            result.put("trace_bucket_size", traceBucket);
            result.put("sample_bucket_size", sampleBucket);

            ArrayNode rows = result.putArray("values");
            for (int t = traceFrom; t < traceTo; t += traceBucket) {
                ArrayNode row = rows.addArray();
                int tTo = Math.min(t + traceBucket, traceTo);
                for (int s = sampleFrom; s < sampleTo; s += sampleBucket) {
                    int sTo = Math.min(s + sampleBucket, sampleTo);
                    double sum = 0;
                    int n = 0;
                    for (int ti = t; ti < tTo; ti++) {
                        Trace trace = traces.get(ti);
                        int limit = Math.min(sTo, trace.numSamples());
                        for (int si = s; si < limit; si++) {
                            sum += trace.getSample(si);
                            n++;
                        }
                    }
                    if (n == 0) {
                        row.addNull();
                    } else {
                        row.add(sum / n);
                    }
                }
            }
            return toJson(result);
        });
    }

    private String removeGprBackground(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        TraceFile traceFile = inFxThread(() -> resolveGprFile(fileName));
        if (traceFile.isBackgroundRemoved()) {
            return "Background is already removed for this file";
        }
        // heavy computation runs on a background thread, as the UI tool does
        traceFile.removeBackground(undoModel);
        inFxThread(() -> {
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
            return null;
        });
        return "Background noise removed";
    }

    private String cropGprSamples(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int offset = args.path("offset").asInt(-1);
        int length = args.path("length").asInt(-1);
        if (offset < 0 || length < 1) {
            throw new IllegalArgumentException("offset must be non-negative and length positive");
        }
        return inFxThread(() -> {
            TraceFile traceFile = resolveGprFile(fileName);
            if (traceFile.getMetaFile() == null) {
                throw new IllegalArgumentException("File does not support sample cropping "
                        + "(it has no metadata sidecar)");
            }
            int samples = traceFile.getMaxSamples();
            if (offset >= samples) {
                throw new IllegalArgumentException("offset " + offset + " is out of bounds, "
                        + "traces have " + samples + " samples");
            }
            traceTransform.cropGprSamples(traceFile, offset, length);
            return "Cropped traces to samples [" + offset + ", "
                    + Math.min(offset + length, samples) + ")";
        });
    }

    private String listScripts() throws Exception {
        List<ScriptMetadata> scripts = scriptMetadataLoader
                .loadScriptMetadata(scriptPaths.getScriptsPath());
        ArrayNode result = mapper.createArrayNode();
        for (ScriptMetadata metadata : scripts) {
            result.add(describeScript(metadata));
        }
        return toJson(result);
    }

    private String getScript(JsonNode args) throws Exception {
        String scriptName = requiredString(args, "script");
        ScriptMetadata metadata = findScript(scriptName);
        Path scriptFile = scriptPaths.getScriptsPath().resolve(metadata.filename());
        ObjectNode result = describeScript(metadata);
        result.put("code", Files.readString(scriptFile));
        return toJson(result);
    }

    private String runScript(JsonNode args) throws Exception {
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
        return status + outputTail(output);
    }

    private String createScript(JsonNode args) throws Exception {
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
        return "Script created: " + scriptFile
                + "; run it with run_script \"" + name + ".py\"";
    }

    private String setPythonPath(JsonNode args) throws Exception {
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
            return "Python interpreter is not configured: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
        try {
            pythonService.checkVersion();
            return "Python interpreter: " + currentPath + " (version check passed, 3.8 or newer)";
        } catch (Exception e) {
            return "Python interpreter: " + currentPath + "; version check failed: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private ObjectNode describeScript(ScriptMetadata metadata) {
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

    private ScriptMetadata findScript(String name) throws IOException {
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

    private static byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode image", e);
        }
    }

    private static byte[] snapshotNode(Node node) {
        WritableImage image = node.snapshot(new SnapshotParameters(), null);
        return toPng(SwingFXUtils.fromFXImage(image, null));
    }

    private ObjectNode imageResult(byte[] png, String text) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "text");
        item.put("text", text);
        ObjectNode image = content.addObject();
        image.put("type", "image");
        image.put("data", Base64.getEncoder().encodeToString(png));
        image.put("mimeType", "image/png");
        return result;
    }

    private SensorLineChart getSensorChart(SgyFile dataFile) {
        if (model.getChart(dataFile) instanceof SensorLineChart sensorChart) {
            return sensorChart;
        }
        throw new IllegalArgumentException("File has no series charts (it is a GPR file); "
                + "use GPR tools instead: get_gpr_info, read_traces, remove_gpr_background, "
                + "crop_gpr_samples");
    }

    private TraceFile resolveGprFile(@Nullable String name) {
        SgyFile dataFile = resolveFile(name);
        if (dataFile instanceof TraceFile traceFile) {
            return traceFile;
        }
        throw new IllegalArgumentException("File is not a GPR file (its kind in list_files is not \"gpr\")");
    }

    private static IndexRange getLineRange(SgyFile dataFile, int line) {
        NavigableMap<Integer, IndexRange> ranges = dataFile.getLineRanges();
        IndexRange range = ranges.get(line);
        if (range == null) {
            throw new IllegalArgumentException("Line not found: " + line
                    + "; file lines: " + ranges.keySet());
        }
        return range;
    }

    private static Range getSeriesRange(SgyFile dataFile, String seriesName) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (GeoData value : dataFile.getGeoData()) {
            Number number = value.getNumber(seriesName);
            if (number != null) {
                min = Math.min(min, number.doubleValue());
                max = Math.max(max, number.doubleValue());
            }
        }
        if (min > max) {
            throw new IllegalArgumentException("Series has no numeric values: " + seriesName);
        }
        return new Range(min, max);
    }

    private static List<Object> parseValues(JsonNode valuesNode) {
        List<Object> values = new ArrayList<>(valuesNode.size());
        for (JsonNode value : valuesNode) {
            if (value.isNull()) {
                values.add(null);
            } else if (value.isNumber()) {
                values.add(value.isIntegralNumber() && value.canConvertToInt()
                        ? value.asInt()
                        : value.asDouble());
            } else {
                throw new IllegalArgumentException("Series values must be numbers or nulls");
            }
        }
        return values;
    }

    @Nullable
    private static List<Integer> readIndices(JsonNode args) {
        JsonNode indicesNode = args.get("indices");
        if (indicesNode == null || indicesNode.isNull()) {
            return null;
        }
        if (!indicesNode.isArray()) {
            throw new IllegalArgumentException("indices must be an array of point indices");
        }
        List<Integer> indices = new ArrayList<>(indicesNode.size());
        for (JsonNode index : indicesNode) {
            if (!index.canConvertToInt()) {
                throw new IllegalArgumentException("indices must be integers");
            }
            indices.add(index.asInt());
        }
        return indices;
    }

    private List<SgyFile> dataFiles() {
        return new ArrayList<>(model.getFileManager().getFiles());
    }

    private static String fileKind(SgyFile file) {
        return file instanceof TraceFile ? "gpr" : "data";
    }

    private SgyFile resolveFile(@Nullable String name) {
        List<SgyFile> dataFiles = dataFiles();
        if (dataFiles.isEmpty()) {
            throw new IllegalArgumentException("No data files are open in GeoHammer");
        }
        if (Strings.isNullOrEmpty(name)) {
            if (dataFiles.size() == 1) {
                return dataFiles.getFirst();
            }
            SgyFile currentFile = model.getCurrentFile();
            if (currentFile != null && dataFiles.contains(currentFile)) {
                return currentFile;
            }
            throw new IllegalArgumentException("Multiple data files are open, "
                    + "specify a file name; open files: " + fileNames(dataFiles));
        }
        for (SgyFile dataFile : dataFiles) {
            File file = dataFile.getFile();
            if (file != null && (file.getName().equals(name) || file.getAbsolutePath().equals(name))) {
                return dataFile;
            }
        }
        throw new IllegalArgumentException("File is not open: " + name
                + "; open files: " + fileNames(dataFiles));
    }

    private static String fileNames(List<SgyFile> dataFiles) {
        List<String> names = new ArrayList<>(dataFiles.size());
        for (SgyFile dataFile : dataFiles) {
            File file = dataFile.getFile();
            if (file != null) {
                names.add(file.getName());
            }
        }
        return String.join(", ", names);
    }

    private static Column getColumn(SgyFile dataFile, String seriesName) {
        ColumnSchema schema = GeoData.getSchema(dataFile.getGeoData());
        Column column = schema != null ? schema.getColumn(seriesName) : null;
        if (column == null) {
            throw new IllegalArgumentException("Series not found: " + seriesName);
        }
        return column;
    }

    private static <T> T inFxThread(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(action.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    private ObjectNode toolResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "text");
        item.put("text", text);
        if (isError) {
            result.put("isError", true);
        }
        return result;
    }

    private String toJson(JsonNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    private static String optionalString(JsonNode args, String name) {
        JsonNode node = args.get(name);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static String requiredString(JsonNode args, String name) {
        String value = optionalString(args, name);
        if (Strings.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    private ObjectNode objectSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    private static ObjectNode addProperty(ObjectNode schema, String name, String type, String description) {
        ObjectNode property = ((ObjectNode) schema.get("properties")).putObject(name);
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static void addFileProperty(ObjectNode schema) {
        addProperty(schema, "file", "string", "Name or full path of a file open in GeoHammer, "
                + "see list_files. Optional when a single file is open "
                + "or a file is selected in the app.");
    }

    private record FilterTarget(SensorLineChart chart, String series) {
    }

    private record GridTarget(SgyFile file, String series) {
    }
}
