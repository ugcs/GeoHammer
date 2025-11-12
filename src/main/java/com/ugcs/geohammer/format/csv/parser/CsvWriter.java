package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CsvWriter {

    private static final String DEFAULT_SEPARATOR = ",";

    private static final boolean DEFAULT_WITH_HEADER = false;

    private final CsvFile csvFile;

    public CsvWriter(CsvFile csvFile) {
        Check.notNull(csvFile);

        this.csvFile = csvFile;
    }

    public void write(File file) throws IOException {
        Check.notNull(file);

        CsvParser parser = csvFile.getParser();
        Template template = csvFile.getTemplate();

        String separator = template != null
                ? template.getFileFormat().getSeparator()
                : DEFAULT_SEPARATOR;
        boolean withHeader = template != null
                ? template.getFileFormat().isHasHeader()
                : DEFAULT_WITH_HEADER;

        // target headers may differ from the source headers
        List<String> headers = getHeadersToSave();
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            // skipped lines
            if (parser != null) {
                for (String line : Nulls.toEmpty(parser.getSkippedLines())) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            // headers
            if (withHeader) {
                writer.write(String.join(separator, buildHeadersLine(headers)));
                writer.newLine();
            }

            // data lines
            for (GeoData value : Nulls.toEmpty(csvFile.getGeoData())) {
                if (value == null) {
                    continue;
                }
                writer.write(String.join(separator, buildDataLine(value, headers)));
                writer.newLine();
            }
        }

        // update parser headers
        if (parser != null) {
            parser.setHeaders(headers);
        }
    }

    private List<String> getHeadersToSave() {
        CsvParser parser = csvFile.getParser();
        List<GeoData> values = Nulls.toEmpty(csvFile.getGeoData());

        if (values.isEmpty()) {
            if (parser != null) {
                // source columns
                return parser.getHeaders();
            } else {
                return List.of();
            }
        }

        List<String> headersToSave = new ArrayList<>();
        ColumnSchema columns = GeoData.getSchema(values);
        for (Column column : Nulls.toEmpty(columns)) {
            String header = column.getHeader();
            // header present in a source file
            // or column has any values
            if (parser != null && parser.hasHeader(header)
                    || !isEmptyColumn(header)) {
                headersToSave.add(header);
            }
        }
        return headersToSave;
    }

    private boolean isEmptyColumn(String header) {
        for (GeoData value : Nulls.toEmpty(csvFile.getGeoData())) {
            if (value != null && value.getValue(header) != null) {
                return false;
            }
        }
        return true;
    }

    private String[] buildHeadersLine(List<String> headers) {
        String[] line = new String[headers.size()];
        for (int i = 0; i < line.length; i++) {
            line[i] = Strings.nullToEmpty(headers.get(i));
        }
        return line;
    }

    private String[] buildDataLine(GeoData value, List<String> headers) {
        String[] line = new String[headers.size()];
        for (int i = 0; i < line.length; i++) {
            String header = headers.get(i);
            Number number = value.getNumber(header);
            if (number != null) {
                line[i] = String.format("%s", number);
            } else {
                String str = value.getString(header);
                line[i] = Strings.nullToEmpty(str);
            }
        }
        return line;
    }
}
