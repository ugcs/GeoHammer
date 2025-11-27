package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.io.LittleEndianDataOutputStream;
import com.ugcs.geohammer.util.Check;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SvlogWriter implements Closeable {

    private final LittleEndianDataOutputStream out;

    public SvlogWriter(File file) throws IOException {
        Check.notNull(file);
        this.out = new LittleEndianDataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file.toPath())));
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public void writePacket(SvlogPacket packet) throws IOException {
        if (packet == null) {
            return;
        }
        out.writeByte(SvlogPacket.SIGNATURE_0);
        out.writeByte(SvlogPacket.SIGNATURE_1);
        out.writeShort(packet.getPayload().length);
        out.writeShort(packet.getPacketId());
        out.writeByte(0);
        out.writeByte(0);
        out.write(packet.getPayload());
        out.writeShort(packet.computeChecksum());
    }
}
