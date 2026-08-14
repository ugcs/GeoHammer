package com.ugcs.geohammer.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class TextFiles {

    private TextFiles() {
    }

    // reads up to maxLines first lines of a file;
    // a line over maxLineLength stops reading
    public static List<String> readLines(File file, int maxLines, int maxLineLength) throws IOException {
        Check.notNull(file);
        Check.condition(maxLines > 0);
        Check.condition(maxLineLength > 0);

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder line = new StringBuilder();
            boolean skipLf = false;
            int c;
            while (lines.size() < maxLines && (c = reader.read()) != -1) {
                if (skipLf) {
                    skipLf = false;
                    if (c == '\n') {
                        continue;
                    }
                }
                if (c == '\n' || c == '\r') {
                    skipLf = c == '\r';
                    lines.add(line.toString());
                    line.setLength(0);
                    continue;
                }
                if (line.length() >= maxLineLength) {
                    line.setLength(0);
                    break;
                }
                line.append((char)c);
            }
            if (!line.isEmpty() && lines.size() < maxLines) {
                lines.add(line.toString());
            }
        }
        return lines;
    }
}
