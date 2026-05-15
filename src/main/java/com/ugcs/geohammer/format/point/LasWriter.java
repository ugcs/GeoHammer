package com.ugcs.geohammer.format.point;

import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.math.UtmProjector;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.proj4j.ProjCoordinate;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

public final class LasWriter implements Closeable {

    private static final int HEADER_SIZE = 227;

    private static final int VLR_HEADER_SIZE = 54;

    private static final int PROJECTION_RECORD_SIZE = 32;

    private static final int POINT_DATA_OFFSET = HEADER_SIZE + VLR_HEADER_SIZE + PROJECTION_RECORD_SIZE;

    private static final byte POINT_RECORD_FORMAT = 0;

    private static final int POINT_RECORD_SIZE = 20;

    // bits 0-2: return number (1), bits 3-5: number of returns (1)
    private static final byte RETURN_1_OF_1 = (byte) (1 | (1 << 3));

    // 1 mm precision
    private static final double COORDINATE_SCALE = 0.001;

    private static final int BUFFER_BYTES = 64 * 1024;

    private final FileChannel channel;

    public LasWriter(Path path) throws IOException {
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

    private static void putString(ByteBuffer buffer, String s, int length) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        int copyLength = Math.min(bytes.length, length);
        buffer.put(bytes, 0, copyLength);
        for (int i = copyLength; i < length; i++) {
            buffer.put((byte) 0);
        }
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

    private static Envelope projectBounds(ScalarPoint.Range pointRange, UtmProjector projection) {
        ProjCoordinate leftBottom = projection.project(
                pointRange.min().x(),
                pointRange.min().y());
        ProjCoordinate leftTop = projection.project(
                pointRange.min().x(),
                pointRange.max().y());
        ProjCoordinate rightBottom = projection.project(
                pointRange.max().x(),
                pointRange.min().y());
        ProjCoordinate rightTop = projection.project(
                pointRange.max().x(),
                pointRange.max().y());

        double minX = Math.min(Math.min(leftBottom.x, leftTop.x), Math.min(rightBottom.x, rightTop.x));
        double minY = Math.min(Math.min(leftBottom.y, leftTop.y), Math.min(rightBottom.y, rightTop.y));

        double maxX = Math.max(Math.max(leftBottom.x, leftTop.x), Math.max(rightBottom.x, rightTop.x));
        double maxY = Math.max(Math.max(leftBottom.y, leftTop.y), Math.max(rightBottom.y, rightTop.y));

        return new Envelope(minX, maxX, minY, maxY);
    }

    private void writeHeader(int numPoints, Envelope bounds, ScalarPoint.Range pointRange)
            throws IOException {
        ByteBuffer buffer = ByteBuffer
                .allocate(HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(new byte[] {'L', 'A', 'S', 'F'});
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        // project id guid
        buffer.putInt(0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.put(new byte[8]);
        // version
        buffer.put((byte) 1);
        buffer.put((byte) 2);
        putString(buffer, "GeoHammer", 32);
        putString(buffer, "GeoHammer", 32);
        LocalDate now = LocalDate.now();
        buffer.putShort((short) now.getDayOfYear());
        buffer.putShort((short) now.getYear());
        buffer.putShort((short) HEADER_SIZE);
        buffer.putInt(POINT_DATA_OFFSET);
        // number of var-length records
        buffer.putInt(1);
        // point record format and size
        buffer.put(POINT_RECORD_FORMAT);
        buffer.putShort((short) POINT_RECORD_SIZE);
        buffer.putInt(numPoints);
        buffer.putInt(numPoints);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        // scales
        buffer.putDouble(COORDINATE_SCALE);
        buffer.putDouble(COORDINATE_SCALE);
        buffer.putDouble(COORDINATE_SCALE);
        // offsets
        buffer.putDouble(Math.floor(bounds.getMinX()));
        buffer.putDouble(Math.floor(bounds.getMinY()));
        buffer.putDouble(0);
        // bounds
        buffer.putDouble(bounds.getMaxX());
        buffer.putDouble(bounds.getMinX());
        buffer.putDouble(bounds.getMaxY());
        buffer.putDouble(bounds.getMinY());
        buffer.putDouble(pointRange.max().z());
        buffer.putDouble(pointRange.min().z());

        buffer.flip();
        writeBuffer(buffer);
    }

    private void writeProjectionRecord(int epsg) throws IOException {
        ByteBuffer buffer = ByteBuffer
                .allocate(VLR_HEADER_SIZE + PROJECTION_RECORD_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        // record header
        buffer.putShort((short) 0);
        putString(buffer, "LASF_Projection", 16);
        // record id: GeoKeyDirectoryTag
        buffer.putShort((short) 34735);
        // record length
        buffer.putShort((short) PROJECTION_RECORD_SIZE);
        putString(buffer, "GeoTIFF GeoKeyDirectoryTag", 32);

        // GeoKeyDirectoryTag header
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0);
        // num keys
        buffer.putShort((short) 3);

        // GTModelTypeGeoKey = 1 (ModelTypeProjected)
        buffer.putShort((short) 1024);
        buffer.putShort((short) 0);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);

        // ProjectedCRSTypeGeoKey = EPSG code for the UTM zone
        buffer.putShort((short) 3072);
        buffer.putShort((short) 0);
        buffer.putShort((short) 1);
        buffer.putShort((short) epsg);

        // ProjLinearUnitsGeoKey = 9001 (Linear_Meter)
        buffer.putShort((short) 3076);
        buffer.putShort((short) 0);
        buffer.putShort((short) 1);
        buffer.putShort((short) 9001);

        buffer.flip();
        writeBuffer(buffer);
    }

    private void writePoints(Path pointsPath, UtmProjector projection, Envelope bounds, ScalarPoint.Range pointRange)
            throws IOException {
        ByteBuffer buffer = ByteBuffer
                .allocateDirect(BUFFER_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);

        double xOrigin = Math.floor(bounds.getMinX());
        double yOrigin = Math.floor(bounds.getMinY());

        float valueRange = pointRange.max().value() - pointRange.min().value();
        float valueScale = valueRange > 0f ? 65535f / valueRange : 0f;

        ProjCoordinate source = new ProjCoordinate();
        ProjCoordinate target = new ProjCoordinate();
        try (ScalarPointReader reader = new ScalarPointReader(pointsPath)) {
            ScalarPoint point;
            while ((point = reader.read()) != null) {
                if (buffer.remaining() < POINT_RECORD_SIZE) {
                    flushBuffer(buffer);
                }

                // project point
                source.x = point.x();
                source.y = point.y();
                projection.project(source, target);
                int x = (int) Math.round((target.x - xOrigin) / COORDINATE_SCALE);
                int y = (int) Math.round((target.y - yOrigin) / COORDINATE_SCALE);
                int z = (int) Math.round(point.z() / COORDINATE_SCALE);
                int intensity = valueScale > 0f
                        ? Math.clamp(Math.round((point.value() - pointRange.min().value()) * valueScale), 0, 65535)
                        : 0;

                // point record
                buffer.putInt(x);
                buffer.putInt(y);
                buffer.putInt(z);
                buffer.putShort((short) intensity);
                buffer.put(RETURN_1_OF_1);
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                buffer.put((byte) 0);
                buffer.putShort((short) 0);
            }
        }
        flushBuffer(buffer);
    }

    public static void writeScalarPoints(Path path, Path pointsPath) throws IOException {
        Check.notNull(path);
        Check.notNull(pointsPath);

        long numPoints = ScalarPointReader.numPoints(pointsPath);
        Check.condition(numPoints <= Integer.MAX_VALUE, "Too many points");

        ScalarPoint.Range pointRange = ScalarPointReader.scanRange(pointsPath);

        LatLon center = SphericalMercator.restore(
                0.5 * (pointRange.min().x() + pointRange.max().x()),
                0.5 * (pointRange.min().y() + pointRange.max().y()));
        UtmProjector projection = new UtmProjector(center.getLatDgr(), center.getLonDgr(),
                UtmProjector.mercator());
        Envelope bounds = projectBounds(pointRange, projection);

        try (LasWriter writer = new LasWriter(path)) {
            writer.writeHeader((int) numPoints, bounds, pointRange);
            writer.writeProjectionRecord(projection.epsgCode());
            writer.writePoints(pointsPath, projection, bounds, pointRange);
        }
    }
}
