package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.util.Check;

public class SvlogPacket {

    public static final byte SIGNATURE_0 = 0x42;

    public static final byte SIGNATURE_1 = 0x52;

    private final int packetId;

    private final byte[] payload;

    public SvlogPacket(int packetId, byte[] payload) {
        Check.condition(packetId >= 0 && packetId <= 0xffff);
        Check.condition(payload.length <= 0xffff);

        this.packetId = packetId;
        this.payload = payload;
    }

    public int getPacketId() {
        return packetId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int computeChecksum() {
        return computeChecksum(0, 0);
    }

    public int computeChecksum(int reserved0, int reserved1) {
        Check.condition(reserved0 >= 0 && reserved0 <= 0xff);
        Check.condition(reserved1 >= 0 && reserved1 <= 0xff);

        // checksum cannot overflow int
        int checksum = SIGNATURE_0 + SIGNATURE_1 + reserved0 + reserved1;
        checksum += (packetId & 0xff) + ((packetId >> 8) & 0xff);
        int n = payload.length;
        checksum += (n & 0xff) + ((n >> 8) & 0xff);
        for (byte b : payload) {
            checksum += b & 0xff;
        }
        return checksum & 0xffff;
    }
}
