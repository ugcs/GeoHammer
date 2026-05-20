package com.ugcs.geohammer.format.point;

import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class PlyWriter implements Closeable {

    private static final int BUFFER_BYTES = 64 * 1024;

    private final FileChannel channel;

    private PlyWriter(Path path) throws IOException {
        Check.notNull(path);
        channel = FileChannel.open(path,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private void writeBuffer(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void flushBuffer(ByteBuffer buffer) throws IOException {
        buffer.flip();
        writeBuffer(buffer);
        buffer.clear();
    }

    private void writeHeader(long numPoints, LatLon origin, String scalar) throws IOException {
        String header = """
                ply
                format binary_little_endian 1.0
                comment created by GeoHammer
                comment origin lat=%s lon=%s alt=0
                element vertex %d
                property double x
                property double y
                property double z
                property float %s
                end_header
                """.formatted(
                origin.getLatDgr(),
                origin.getLonDgr(),
                numPoints,
                scalar);
        ByteBuffer buffer = ByteBuffer.wrap(header.getBytes(StandardCharsets.US_ASCII));
        writeBuffer(buffer);
    }

    private void writePoints(Path pointsPath, ScalarPoint.Range pointRange, LatLon origin) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);

        double kInverted = 1.0 / SphericalMercator.scaleFactorAt(origin.getLatDgr());
        try (ScalarPointReader reader = new ScalarPointReader(pointsPath)) {
            ScalarPoint point;
            while ((point = reader.read()) != null) {
                if (buffer.remaining() < ScalarPoint.BYTES) {
                    flushBuffer(buffer);
                }

                buffer.putDouble(kInverted * (point.x() - pointRange.min().x()));
                buffer.putDouble(kInverted * (point.y() - pointRange.min().y()));
                buffer.putDouble(point.z());
                buffer.putFloat(point.value());
            }
            flushBuffer(buffer);
        }
    }

    public static void writeScalarPoints(Path path, Path pointsPath, String scalar) throws IOException {
        Check.notNull(path);
        Check.notNull(pointsPath);
        Check.notEmpty(scalar);

        long numPoints = ScalarPointReader.numPoints(pointsPath);
        ScalarPoint.Range pointRange = ScalarPointReader.scanRange(pointsPath);
        LatLon origin = SphericalMercator.restore(
                pointRange.min().x(),
                pointRange.min().y());

        try (PlyWriter writer = new PlyWriter(path)) {
            writer.writeHeader(numPoints, origin, scalar);
            writer.writePoints(pointsPath, pointRange, origin);
        }
    }
}
