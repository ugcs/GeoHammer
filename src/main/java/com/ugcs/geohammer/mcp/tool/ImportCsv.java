package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.io.csv.CsvReader;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.util.Numbers;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ImportCsv extends McpTool {

    private static final Logger log = LoggerFactory.getLogger(ImportCsv.class);

    private final UndoModel undoModel;

    public ImportCsv(Model model, UndoModel undoModel) {
        super(model);
        this.undoModel = undoModel;
    }

    @Override
    public String getName() {
        return "import_csv";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Load series values from a CSV file on disk into an open data file. "
                + "This is the way to write bulk results back after {{export_csv}}: process the export "
                + "locally, save it as CSV and import it here instead of passing values through "
                + "{{write_series}} or {{create_series}}. "
                + "The CSV must have a header row and exactly one row per point of the open file, in file "
                + "order: row N is written to point N, there is no row matching by index or coordinates, and "
                + "the import is rejected if the row count differs from the point count. Do not drop, reorder "
                + "or append rows. Columns are matched to series by header: an existing series is overwritten, "
                + "an unknown header creates a new series. By default every column is imported; pass 'series' "
                + "to import selected columns only. Cells may be numbers or text, an empty cell is a null. "
                + "If the mark series is imported, marks are recreated from it (non-zero means marked). "
                + "A failed import leaves the file unchanged. The change is shown in the app immediately, "
                + "supports undo and marks the file as unsaved.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "path", "string", "Full path of the CSV file to import.");
        ObjectNode series = addProperty(schema, "series", "array",
                "Optional headers of the columns to import. Default: all columns.");
        series.putObject("items").put("type", "string");
        schema.withArrayProperty("required").add("path");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        Path path = Path.of(requiredString(args, "path"));
        List<String> seriesNames = optionalStrings(args, "series");
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }

        SgyFile dataFile = resolveFile(fileName);
        if (dataFile instanceof TraceFile) {
            throw new IllegalArgumentException("{{import_csv}} supports data files only (csv, sonar, nmea)");
        }
        if (GeoData.getSchema(dataFile.getGeoData()) == null) {
            throw new IllegalArgumentException("File has no data");
        }

        // snapshot and parsing take seconds on a large file, keep them off the FX thread;
        // values are streamed straight into the file and rolled back from the snapshot on error
        FileSnapshot<? extends SgyFile> snapshot = dataFile.createSnapshot();
        if (snapshot == null) {
            throw new IllegalStateException("Failed to create a snapshot of the file");
        }
        ImportResult result;
        try {
            result = readInto(path, dataFile, seriesNames);
        } catch (Exception e) {
            if (!rollback(snapshot, dataFile)) {
                throw new IllegalStateException(e.getMessage()
                        + "; rolling the file back failed too, its data may be partially imported", e);
            }
            throw e;
        }

        return text(inFxThread(() -> {
            undoModel.push(new UndoFrame(snapshot));
            dataFile.setUnsaved(true);
            model.reload(dataFile);

            ObjectNode node = mapper.createObjectNode();
            node.put("points", dataFile.getGeoData().size());
            node.putPOJO("updated", result.updated());
            node.putPOJO("created", result.created());
            if (result.marks() >= 0) {
                node.put("marks", result.marks());
            }
            return toJson(node);
        }));
    }

    private record ImportResult(List<String> updated, List<String> created, int marks) {
    }

    private ImportResult readInto(Path path, SgyFile dataFile, List<String> seriesNames) throws IOException {
        List<GeoData> geoData = dataFile.getGeoData();
        ColumnSchema schema = GeoData.getSchema(geoData);
        try (CsvReader reader = new CsvReader(Files.newBufferedReader(path))) {
            List<String> headers = reader.readFields();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty: " + path);
            }
            List<String> selected = seriesNames.isEmpty() ? headers : seriesNames;
            int[] csvIndices = new int[selected.size()];
            int[] valueIndices = new int[selected.size()];
            boolean[] hasNumbers = new boolean[selected.size()];
            List<String> updated = new ArrayList<>();
            List<String> created = new ArrayList<>();
            for (int j = 0; j < selected.size(); j++) {
                String header = selected.get(j);
                if (header.isBlank()) {
                    throw new IllegalArgumentException("CSV has a blank header in column " + (j + 1));
                }
                int csvIndex = headers.indexOf(header);
                if (csvIndex == -1) {
                    throw new IllegalArgumentException("Column not found in CSV: " + header
                            + ", available columns: " + String.join(", ", headers));
                }
                if (headers.lastIndexOf(header) != csvIndex) {
                    throw new IllegalArgumentException("CSV has a duplicate header: " + header);
                }
                csvIndices[j] = csvIndex;
                if (schema.getColumn(header) == null) {
                    GeoData.addColumn(geoData, new Column(header));
                    created.add(header);
                } else {
                    updated.add(header);
                }
                valueIndices[j] = schema.getColumnIndex(header);
            }

            int numPoints = geoData.size();
            int row = 0;
            List<String> fields;
            while ((fields = reader.readFields()) != null) {
                if (row == numPoints) {
                    throw new IllegalArgumentException("CSV has more rows than the file has points ("
                            + numPoints + "); the import needs exactly one row per point in file order");
                }
                if (fields.size() != headers.size()) {
                    throw new IllegalArgumentException("Row " + (row + 2) + " has " + fields.size()
                            + " fields, expected " + headers.size());
                }
                GeoData value = geoData.get(row);
                for (int j = 0; j < csvIndices.length; j++) {
                    Object cell = parseCell(fields.get(csvIndices[j]));
                    if (cell instanceof Number) {
                        hasNumbers[j] = true;
                    }
                    value.setValue(valueIndices[j], cell);
                }
                row++;
            }
            if (row != numPoints) {
                throw new IllegalArgumentException("CSV has " + row + " rows but the file has " + numPoints
                        + " points; the import needs exactly one row per point in file order");
            }

            for (int j = 0; j < selected.size(); j++) {
                if (created.contains(selected.get(j))) {
                    schema.getColumn(selected.get(j)).setDisplay(hasNumbers[j]);
                }
            }
            String markHeader = schema.getHeaderBySemantic(Semantic.MARK.getName());
            int marks = markHeader != null && selected.contains(markHeader)
                    ? rebuildMarks(dataFile)
                    : -1;
            return new ImportResult(updated, created, marks);
        }
    }

    private static @Nullable Object parseCell(String cell) {
        Numbers.ParseResult parsed = Numbers.parseNumber(cell);
        if (parsed.valid()) {
            return parsed.number();
        }
        String text = cell.strip();
        return text.equalsIgnoreCase("nan") ? null : text;
    }

    private int rebuildMarks(SgyFile dataFile) {
        List<BaseObject> auxElements = dataFile.getAuxElements();
        Iterator<BaseObject> it = auxElements.iterator();
        while (it.hasNext()) {
            if (it.next() instanceof FoundPlace) {
                it.remove();
            }
        }
        List<GeoData> geoData = dataFile.getGeoData();
        int marks = 0;
        for (int i = 0; i < geoData.size(); i++) {
            if (geoData.get(i).getMarkOrDefault(false)) {
                auxElements.add(new FoundPlace(new TraceKey(dataFile, i), model));
                marks++;
            }
        }
        return marks;
    }

    private boolean rollback(FileSnapshot<? extends SgyFile> snapshot, SgyFile dataFile) {
        try {
            snapshot.restoreFile(model);
            inFxThread(() -> {
                model.reload(dataFile);
                return null;
            });
            return true;
        } catch (Exception e) {
            log.error("Failed to roll back import", e);
            return false;
        } finally {
            snapshot.discard();
        }
    }
}
