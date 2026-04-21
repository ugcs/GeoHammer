package com.ugcs.geohammer.util;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipStream implements Closeable {

    private final ZipOutputStream zip;

    private final Set<String> entryNames = new HashSet<>();

    public ZipStream(OutputStream out) {
        Check.notNull(out);

        zip = new ZipOutputStream(new BufferedOutputStream(out));
    }

    private String toEntryName(String fileName) {
        if (entryNames.add(fileName)) {
            return fileName;
        }
        int i = 1;
        String entryName;
        while (!entryNames.add(entryName
                = FileNames.addSuffix(fileName, Integer.toString(i)))) {
            i++;
        }
        return entryName;
    }

    public void addFile(String fileName, InputStream in) throws IOException {
        Check.notEmpty(fileName);
        Check.notNull(in);

        String entryName = toEntryName(fileName);
        zip.putNextEntry(new ZipEntry(entryName));
        try {
            in.transferTo(zip);
        } finally {
            zip.closeEntry();
        }
    }

    public void addFile(String fileName, byte[] bytes) throws IOException {
        Check.notNull(bytes);

        try (InputStream in = new ByteArrayInputStream(bytes)) {
            addFile(fileName, in);
        }
    }

    public void addFile(Path path) throws IOException {
        Check.notNull(path);

        try (InputStream in = Files.newInputStream(path)) {
            addFile(path.getFileName().toString(), in);
        }
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }
}