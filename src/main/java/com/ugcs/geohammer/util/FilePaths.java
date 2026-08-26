package com.ugcs.geohammer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class FilePaths {

    private static final Logger log = LoggerFactory.getLogger(FilePaths.class);

    private FilePaths() {
    }

    public static List<File> existingFiles(List<String> paths) {
        List<File> files = new ArrayList<>();
        for (String path : Nulls.toEmpty(paths)) {
            File file = new File(path);
            if (file.exists()) {
                files.add(file.getAbsoluteFile());
            } else {
                log.warn("File not found: {}", path);
            }
        }
        return files;
    }
}
