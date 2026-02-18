package com.ugcs.geohammer.util;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Resources {

    private Resources() {
    }

    public static InputStream get(@NonNull String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream in = loader.getResourceAsStream(path);
        if (in != null) {
            return in;
        }
        try {
            return Files.newInputStream(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
