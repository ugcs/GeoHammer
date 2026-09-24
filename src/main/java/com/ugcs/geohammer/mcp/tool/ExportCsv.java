package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.io.csv.CsvWriter;
import com.ugcs.geohammer.mcp.McpSession;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.util.AuxElements;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                + "The export contains all points, one row per point in file order, and by default all series "
                + "exactly as {{list_series}} reports them, including series created in this session by "
                + "{{create_series}} and {{apply_filter}}, and reflects every modification made so far "
                + "(cuts, edits, filters). Pass 'series' to export selected series only. "
                + "Do NOT read the file's original path from disk instead: that copy is the unmodified "
                + "file as opened and does not contain any in-session changes. "
                + "The temporary file is not cleaned up automatically. Write results back with {{import_csv}}: "
                + "process the export locally, keep every row in place and add or update columns, then import "
                + "the result. Passing the values through {{write_series}} or {{create_series}} instead "
                + "consumes context very quickly on large files.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        ObjectNode series = addProperty(schema, "series", "array",
                "Optional names of the series to export, in this order. Default: all series.");
        series.putObject("items").put("type", "string");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(McpSession session, JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        List<String> seriesNames = optionalStrings(args, "series");
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
        List<String> headers = new ArrayList<>();
        if (seriesNames.isEmpty()) {
            for (Column column : schema) {
                headers.add(column.getHeader());
            }
        } else {
            for (String seriesName : seriesNames) {
                if (schema.getColumn(seriesName) == null) {
                    throw new IllegalArgumentException("Series not found: " + seriesName);
                }
                if (!headers.contains(seriesName)) {
                    headers.add(seriesName);
                }
            }
        }
        // copy the list: an edit in the app while the file is written must not break the iteration
        ExportTarget target = new ExportTarget(dataFile.getFile(), headers,
                schema.getHeaderBySemantic(Semantic.MARK.getName()),
                new ArrayList<>(geoData), AuxElements.getMarkIndices(dataFile.getAuxElements()));

        File source = target.source();
        String prefix = source != null
                ? FileNames.removeExtension(source.getName()) + "-"
                : "geohammer-";
        Path path = Files.createTempFile(prefix, ".csv");
        writeCsv(path, target);

        ObjectNode result = mapper.createObjectNode();
        result.put("path", path.toAbsolutePath().toString());
        result.put("points", target.geoData().size());
        result.put("series", target.headers().size());
        result.put("bytes", Files.size(path));
        return text(toJson(result));
    }

    private record ExportTarget(@Nullable File source, List<String> headers, @Nullable String markHeader,
            List<GeoData> geoData, Set<Integer> marks) {
    }

    private static void writeCsv(Path path, ExportTarget target) throws IOException {
        List<String> headers = target.headers();
        try (CsvWriter writer = new CsvWriter(Files.newBufferedWriter(path))) {
            writer.writeFields(headers);
            List<String> row = new ArrayList<>(headers.size());
            List<GeoData> geoData = target.geoData();
            for (int i = 0; i < geoData.size(); i++) {
                GeoData value = geoData.get(i);
                row.clear();
                for (String header : headers) {
                    if (header.equals(target.markHeader())) {
                        // marks live in aux elements, the mark series is only synced on save
                        row.add(target.marks().contains(i) ? "1" : "0");
                        continue;
                    }
                    Object cell = value.getValue(header);
                    row.add(cell != null ? cell.toString() : Strings.empty());
                }
                writer.writeFields(row);
            }
        }
    }
}
