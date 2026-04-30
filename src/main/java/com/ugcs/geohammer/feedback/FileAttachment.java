package com.ugcs.geohammer.feedback;

import com.ugcs.geohammer.util.Check;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileAttachment implements Attachment {

    private final Path path;

    public FileAttachment(Path path) {
        this.path = Check.notNull(path);
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getFileName() {
        return path.getFileName().toString();
    }

    @Override
    public InputStream getInput() {
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
