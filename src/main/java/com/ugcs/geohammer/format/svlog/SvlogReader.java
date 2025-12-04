package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.io.LittleEndianDataInputStream;
import com.ugcs.geohammer.util.Check;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SvlogReader implements Closeable {

    private final LittleEndianDataInputStream in;

    public SvlogReader(File file) throws IOException {
        Check.notNull(file);
        this.in = new LittleEndianDataInputStream(
                new BufferedInputStream(Files.newInputStream(file.toPath())));
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public SvlogPacket readPacket() throws IOException {
        try {
            int signature0 = in.readUnsignedByte();
            Check.condition(signature0 == SvlogPacket.SIGNATURE_0);
            int signature1 = in.readUnsignedByte();
            Check.condition(signature1 == SvlogPacket.SIGNATURE_1);

            int payloadLength = in.readUnsignedShort();
            int packetId = in.readUnsignedShort();
            int reserved0 = in.readUnsignedByte();
            int reserved1 = in.readUnsignedByte();
            byte[] payload = new byte[payloadLength];
            in.readFully(payload);
            int checksum = in.readUnsignedShort();

            SvlogPacket packet = new SvlogPacket(packetId, payload);
            Check.condition(checksum == packet.computeChecksum(reserved0, reserved1));
            return packet;
        } catch (EOFException e) {
            return null;
        }
    }
}
