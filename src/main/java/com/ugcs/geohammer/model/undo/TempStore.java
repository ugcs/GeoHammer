package com.ugcs.geohammer.model.undo;

import com.ugcs.geohammer.util.Check;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TempStore {

    private static final Logger log = LoggerFactory.getLogger(TempStore.class);

    private static final String DIRECTORY_PREFIX = "geohammer-tmp-";

    private static final String FILE_SUFFIX = ".bin";

    private final Path basePath;

    private final AtomicLong sequence = new AtomicLong();

    public TempStore() throws IOException {
        this.basePath = Files.createTempDirectory(DIRECTORY_PREFIX);
        log.info("Temp directory created: {}", basePath);
    }

    public Entry newEntry() {
        Path path = basePath.resolve(sequence.incrementAndGet() + FILE_SUFFIX);
        return new Entry(path);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
        }
    }

    @PreDestroy
    public void close() {
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(basePath)) {
            for (Path path : paths) {
                deleteQuietly(path);
            }
        } catch (IOException e) {
            log.warn("Failed to list directory {}", basePath, e);
        }
        deleteQuietly(basePath);
    }

    @FunctionalInterface
    public interface Writer {

        void write(DataOutputStream out) throws IOException;
    }

    @FunctionalInterface
    public interface Reader<T> {

        T read(DataInputStream in) throws IOException;
    }

    public static final class Entry implements Closeable {

        private final Path path;

        Entry(Path path) {
            this.path = Check.notNull(path);
        }

        public void write(Writer writer) throws IOException {
            Check.notNull(writer);
            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(path)))) {
                writer.write(out);
            } catch (Exception e) {
                deleteQuietly(path);
                throw e;
            }
        }

        public <T> T read(Reader<T> reader) throws IOException {
            Check.notNull(reader);
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(path)))) {
                return reader.read(in);
            }
        }

        @Override
        public void close() {
            deleteQuietly(path);
        }
    }
}
