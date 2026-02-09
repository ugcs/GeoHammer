package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.template.FileFormat;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CsvWriter extends Writer {

    public CsvWriter(Template template) {
        super(template);
    }

    @Override
    public void write(CsvFile csvFile, File toFile) throws IOException {
        Check.notNull(csvFile);
        Check.notNull(toFile);

        if (template.isReadOnly()) {
            throw new IllegalStateException(template.getName() + " files are read only");
        }

        Parser parser = csvFile.getParser();
        FileFormat format = template.getFileFormat();

        // target headers may differ from the source headers
        List<String> headers = getHeadersToSave(csvFile);
        try (BufferedWriter writer = Files.newBufferedWriter(toFile.toPath())) {
            // skipped lines
            if (parser != null) {
                for (String line : Nulls.toEmpty(parser.getSkippedLines())) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            // headers
            if (format.isHasHeader()) {
                writer.write(String.join(format.getSeparator(), buildHeadersLine(headers)));
                writer.newLine();
            }

            // value lines
            for (GeoData value : csvFile.getGeoData()) {
                if (value == null) {
                    continue;
                }
                writer.write(String.join(format.getSeparator(), buildDataLine(value, headers)));
                writer.newLine();
            }
        }

        // update parser headers
        if (parser != null) {
            parser.setHeaders(headers);
        }
    }

    private List<String> getHeadersToSave(CsvFile csvFile) {
        Parser parser = csvFile.getParser();
        List<GeoData> values = csvFile.getGeoData();

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
                    || !isEmptyColumn(header, values)) {
                headersToSave.add(header);
            }
        }
        return headersToSave;
    }

    private boolean isEmptyColumn(String header, List<GeoData> values) {
        for (GeoData value : values) {
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
                line[i] = Text.formatNumber(number);
            } else {
                String str = value.getString(header);
                line[i] = Strings.nullToEmpty(str);
            }
        }
        return line;
    }
}
