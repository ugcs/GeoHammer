package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExportCsv extends McpTool {

    public ExportCsv(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "export_csv";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Write the current data of an open data file to a temporary CSV file "
                + "and return its path, for full-resolution analysis with local tools. "
                + "Prefer this over paging {{read_series}} when a task needs every point: {{read_data}} is capped "
                + "at " + MAX_READ_COUNT + " buckets per series and is only meant for overviews. "
                + "The export contains all points and all series exactly as {{list_series}} reports them, "
                + "including series created in this session by {{create_series}} and {{apply_filter}}, and "
                + "reflects every modification made so far (cuts, edits, filters). "
                + "Do NOT read the file's original path from disk instead: that copy is the unmodified "
                + "file as opened and does not contain any in-session changes. "
                + "The temporary file is not cleaned up automatically; write results back with "
                + "{{write_series}} or {{create_series}}.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        ExportTarget target = inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            if (dataFile instanceof TraceFile) {
                throw new IllegalArgumentException("{{export_csv}} supports data files only "
                        + "(csv, sonar, nmea); read GPR traces with {{read_traces}}");
            }
            List<GeoData> geoData = dataFile.getGeoData();
            ColumnSchema schema = GeoData.getSchema(geoData);
            if (schema == null) {
                throw new IllegalArgumentException("File has no data series to export");
            }
            // copy the list while on the FX thread: the write below runs off it
            return new ExportTarget(dataFile.getFile(), schema,
                    new ArrayList<>(geoData), markedPoints(dataFile));
        });

        // formatting a large file takes seconds, keep it off the FX thread
        File source = target.source();
        String prefix = source != null
                ? FileNames.removeExtension(source.getName()) + "-"
                : "geohammer-";
        Path path = Files.createTempFile(prefix, ".csv");
        writeCsv(path, target.schema(), target.geoData(), target.marks());

        ObjectNode result = mapper.createObjectNode();
        result.put("path", path.toAbsolutePath().toString());
        result.put("points", target.geoData().size());
        result.put("series", target.schema().numColumns());
        result.put("bytes", Files.size(path));
        return text(toJson(result));
    }

    private record ExportTarget(@Nullable File source, ColumnSchema schema,
            List<GeoData> geoData, Set<Integer> marks) {
    }

    private static Set<Integer> markedPoints(SgyFile dataFile) {
        Set<Integer> marks = new HashSet<>();
        for (BaseObject element : Nulls.toEmpty(dataFile.getAuxElements())) {
            if (element instanceof FoundPlace flag) {
                marks.add(flag.getTraceIndex());
            }
        }
        return marks;
    }

    private static void writeCsv(Path path, ColumnSchema schema, List<GeoData> geoData,
            Set<Integer> marks) throws IOException {
        List<String> headers = new ArrayList<>(schema.numColumns());
        for (Column column : schema) {
            headers.add(column.getHeader());
        }
        String markHeader = schema.getHeaderBySemantic(Semantic.MARK.getName());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writeCsvRow(writer, headers);
            List<String> row = new ArrayList<>(headers.size());
            for (int i = 0; i < geoData.size(); i++) {
                GeoData value = geoData.get(i);
                row.clear();
                for (String header : headers) {
                    if (header.equals(markHeader)) {
                        row.add(marks.contains(i) ? "1" : "0");
                        continue;
                    }
                    Object cell = value.getValue(header);
                    row.add(cell != null ? cell.toString() : Strings.empty());
                }
                writeCsvRow(writer, row);
            }
        }
    }

    private static void writeCsvRow(BufferedWriter writer, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            String cell = cells.get(i);
            if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0) {
                writer.write('"');
                writer.write(cell.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(cell);
            }
        }
        writer.write('\n');
    }
}
