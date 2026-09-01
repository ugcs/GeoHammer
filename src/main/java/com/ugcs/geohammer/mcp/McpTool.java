package com.ugcs.geohammer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.nmea.NmeaFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// Base of a single MCP tool: it declares its own name and schema and executes calls.
// Tool descriptions may reference other tools as {{tool_name}}; McpTools resolves
// those placeholders against the registry when the tool list is built.
public abstract class McpTool {

    protected static final int DEFAULT_READ_COUNT = 1000;

    protected static final int MAX_READ_COUNT = 10000;

    private static final int CALL_TIMEOUT_SECONDS = 30;

    protected static final ObjectMapper mapper = new ObjectMapper();

    protected final Model model;

    protected McpTool(Model model) {
        this.model = model;
    }

    public abstract String getName();

    // full tool descriptor: name, description and inputSchema
    public abstract ObjectNode buildSchema();

    public abstract ObjectNode invoke(JsonNode args) throws Exception;

    // schema

    protected ObjectNode descriptor(String description) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", getName());
        tool.put("description", description);
        return tool;
    }

    protected static ObjectNode objectSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    protected static ObjectNode addProperty(ObjectNode schema, String name, String type, String description) {
        ObjectNode property = ((ObjectNode) schema.get("properties")).putObject(name);
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    protected static void addFileProperty(ObjectNode schema) {
        addProperty(schema, "file", "string", "Name or full path of a file open in GeoHammer, "
                + "see {{list_files}}. Optional when a single file is open "
                + "or a file is selected in the app.");
    }

    // results

    protected static ObjectNode text(String body) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "text");
        item.put("text", body);
        return result;
    }

    protected static ObjectNode image(byte[] png, String caption) {
        ObjectNode result = text(caption);
        ObjectNode image = ((ArrayNode) result.get("content")).addObject();
        image.put("type", "image");
        image.put("data", Base64.getEncoder().encodeToString(png));
        image.put("mimeType", "image/png");
        return result;
    }

    protected static String toJson(JsonNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // arguments

    @Nullable
    protected static String optionalString(JsonNode args, String name) {
        JsonNode node = args.get(name);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    protected static String requiredString(JsonNode args, String name) {
        String value = optionalString(args, name);
        if (Strings.isNullOrEmpty(value)) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return value;
    }

    @Nullable
    protected static List<Integer> readIndices(JsonNode args) {
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

    protected static List<Object> parseValues(JsonNode valuesNode) {
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

    // files

    protected List<SgyFile> dataFiles() {
        return new ArrayList<>(model.getFileManager().getFiles());
    }

    protected SgyFile resolveFile(@Nullable String name) {
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

    protected TraceFile resolveGprFile(@Nullable String name) {
        SgyFile dataFile = resolveFile(name);
        if (dataFile instanceof TraceFile traceFile) {
            return traceFile;
        }
        throw new IllegalArgumentException("File is not a GPR file "
                + "(its type in {{list_files}} is not \"gpr\")");
    }

    protected static String fileType(SgyFile file) {
        return switch (file) {
            // trace files first: GprFile and DztFile both extend TraceFile
            case TraceFile traceFile -> "gpr";
            case CsvFile csvFile -> "csv";
            case SonarFile sonarFile -> "sonar";
            case NmeaFile nmeaFile -> "nmea";
            default -> "unknown";
        };
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

    // series and lines

    protected static Column getColumn(SgyFile dataFile, String seriesName) {
        ColumnSchema schema = GeoData.getSchema(dataFile.getGeoData());
        Column column = schema != null ? schema.getColumn(seriesName) : null;
        if (column == null) {
            throw new IllegalArgumentException("Series not found: " + seriesName);
        }
        return column;
    }

    protected static IndexRange getLineRange(SgyFile dataFile, int line) {
        NavigableMap<Integer, IndexRange> ranges = dataFile.getLineRanges();
        IndexRange range = ranges.get(line);
        if (range == null) {
            throw new IllegalArgumentException("Line not found: " + line
                    + "; file lines: " + ranges.keySet());
        }
        return range;
    }

    @Nullable
    protected static DataMapping dataMapping(SgyFile dataFile) {
        Template template = Templates.getTemplate(dataFile);
        return template != null ? template.getDataMapping() : null;
    }

    // series descriptions are static template metadata, not a part of the file data
    @Nullable
    protected static String seriesDescription(@Nullable DataMapping mapping, String seriesName) {
        if (mapping == null) {
            return null;
        }
        SensorData dataValue = mapping.getDataValueByHeader(seriesName);
        return dataValue != null ? Strings.emptyToNull(dataValue.getDescription()) : null;
    }

    protected SensorLineChart getSensorChart(SgyFile dataFile) {
        if (model.getChart(dataFile) instanceof SensorLineChart sensorChart) {
            return sensorChart;
        }
        throw new IllegalArgumentException("File has no series charts (it is a GPR file); "
                + "use GPR tools instead: {{get_gpr_info}}, {{read_traces}}, "
                + "{{remove_gpr_background}}, {{crop_gpr_samples}}");
    }

    // charts and images

    protected static byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode image", e);
        }
    }

    protected static byte[] snapshotNode(Node node) {
        WritableImage image = node.snapshot(new SnapshotParameters(), null);
        return toPng(SwingFXUtils.fromFXImage(image, null));
    }

    // execution

    protected static <T> T inFxThread(Callable<T> action) throws Exception {
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
}
