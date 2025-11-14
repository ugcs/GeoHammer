package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class FixedWidthWriter extends Writer {

    public FixedWidthWriter(Template template) {
        super(template);
    }

    @Override
    public void write(CsvFile csvFile, File toFile) throws IOException {
        Check.notNull(csvFile);
        Check.notNull(toFile);

        Parser parser = csvFile.getParser();

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
            // value lines
            for (GeoData value : csvFile.getGeoData()) {
                if (value == null) {
                    continue;
                }
                writer.write(buildDataLine(value, headers));
                writer.newLine();
            }
        }
    }

    private List<String> getHeadersToSave(CsvFile csvFile) {
        Parser parser = csvFile.getParser();
        return parser != null
                ? parser.getHeaders()
                : template.getDataMapping().getIndexedHeaders();
    }

    private String buildDataLine(GeoData value, List<String> headers) {
        List<Short> widths = template.getFileFormat().getColumnLengths();

        StringBuilder line = new StringBuilder();
        int n = Math.min(widths.size(), headers.size());
        for (int i = 0; i < n; i++) {
            String header = headers.get(i);
            int width = widths.get(i);

            Number number = value.getNumber(header);
            String str = number != null
                    ? Text.formatNumber(number)
                    : Strings.nullToEmpty(value.getString(header));
            if (str.length() > width) {
                str = str.substring(0, width);
            }
            while (width-- > str.length()) {
                line.append(" ");
            }
            line.append(str);
        }
        return line.toString();
    }
}
