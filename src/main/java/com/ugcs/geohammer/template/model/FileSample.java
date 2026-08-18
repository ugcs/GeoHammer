package com.ugcs.geohammer.template.model;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.TextFiles;

import java.io.File;
import java.io.IOException;
import java.util.List;

public record FileSample(File file, List<String> lines) {

    public static final int DEFAULT_SAMPLE_LINES = 200;

    public static final int MAX_LINE_LENGTH = 4096;

    public FileSample {
        Check.notNull(file);
        Check.notNull(lines);
    }

    public static FileSample read(File file) throws IOException {
        List<String> lines = TextFiles.readLines(file, DEFAULT_SAMPLE_LINES, MAX_LINE_LENGTH);
        return new FileSample(file, lines);
    }

    public int numLines() {
        return lines.size();
    }

    public String line(int index) {
        return lines.get(index);
    }
}
