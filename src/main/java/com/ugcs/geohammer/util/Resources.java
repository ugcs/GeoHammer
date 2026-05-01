package com.ugcs.geohammer.util;

import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Resources {

    private Resources() {
    }

    public static InputStream get(@NonNull String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = Resources.class.getClassLoader();
        }
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        if (loader != null) {
            InputStream in = loader.getResourceAsStream(path);
            if (in != null) {
                return in;
            }
        }
        try {
            return Files.newInputStream(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path resolvePath(String path) {
        Check.notNull(path);

        return resolvePath(Path.of(path));
    }

    public static Path resolvePath(Path path) {
        Check.notNull(path);

        Path resolved = path;
        if (!Files.exists(resolved)) {
            Path currentPath = null;
            try {
                currentPath = Paths.get(Resources.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()).getParent();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            resolved = currentPath.resolve(path);
        }
        return resolved;
    }
}
