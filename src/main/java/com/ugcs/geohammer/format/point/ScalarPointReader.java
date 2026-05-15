package com.ugcs.geohammer.format.point;

import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ScalarPointReader implements Closeable {

    private final FileChannel channel;

    private final ByteBuffer buffer;

    private boolean eof;

    public ScalarPointReader(Path path) throws IOException {
        Check.notNull(path);
        channel = FileChannel.open(path, StandardOpenOption.READ);
        buffer = ByteBuffer.allocateDirect(4096 * ScalarPoint.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.flip();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public static long numPoints(Path path) throws IOException {
        Check.notNull(path);
        return Files.size(path) / ScalarPoint.BYTES;
    }

    public static ScalarPoint.Range scanRange(Path path) throws IOException {
        Check.notNull(path);

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        float minValue = Float.POSITIVE_INFINITY;
        float maxValue = Float.NEGATIVE_INFINITY;

        try (ScalarPointReader reader = new ScalarPointReader(path)) {
            ScalarPoint point;
            while ((point = reader.read()) != null) {
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                minY = Math.min(minY, point.y());
                maxY = Math.max(maxY, point.y());
                minZ = Math.min(minZ, point.z());
                maxZ = Math.max(maxZ, point.z());
                minValue = Math.min(minValue, point.value());
                maxValue = Math.max(maxValue, point.value());
            }
        }

        return new ScalarPoint.Range(
                new ScalarPoint(minX, minY, minZ, minValue),
                new ScalarPoint(maxX, maxY, maxZ, maxValue)
        );
    }

    public @Nullable ScalarPoint read() throws IOException {
        if (!ensureAvailable()) {
            return null;
        }
        double x = buffer.getDouble();
        double y = buffer.getDouble();
        double z = buffer.getDouble();
        float value = buffer.getFloat();
        return new ScalarPoint(x, y, z, value);
    }

    private boolean ensureAvailable() throws IOException {
        while (buffer.remaining() < ScalarPoint.BYTES && !eof) {
            buffer.compact();
            int n = channel.read(buffer);
            if (n == -1) {
                eof = true;
            }
            buffer.flip();
        }
        return buffer.remaining() >= ScalarPoint.BYTES;
    }
}
