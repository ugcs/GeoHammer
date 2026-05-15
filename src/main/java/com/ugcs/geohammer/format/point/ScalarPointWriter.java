package com.ugcs.geohammer.format.point;

import com.ugcs.geohammer.util.Check;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ScalarPointWriter implements Closeable {

    private final FileChannel channel;

    private final ByteBuffer buffer;

    private long count;

    public ScalarPointWriter(Path path) throws IOException {
        Check.notNull(path);
        channel = FileChannel.open(path,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        buffer = ByteBuffer.allocateDirect(4096 * ScalarPoint.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    public void write(ScalarPoint point) throws IOException {
        Check.notNull(point);
        if (buffer.remaining() < ScalarPoint.BYTES) {
            flush();
        }
        buffer.putDouble(point.x());
        buffer.putDouble(point.y());
        buffer.putDouble(point.z());
        buffer.putFloat(point.value());
        count++;
    }

    public long getCount() {
        return count;
    }

    private void flush() throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }

    @Override
    public void close() throws IOException {
        try {
            flush();
        } finally {
            channel.close();
        }
    }
}
