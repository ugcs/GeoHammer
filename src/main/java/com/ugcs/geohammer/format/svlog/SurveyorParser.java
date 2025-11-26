package com.ugcs.geohammer.format.svlog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SurveyorParser {

    public Double getDepth(SvlogPacket packet) {
        if (packet.getPacketId() != SvlogPacketId.SURVEYOR_ATOF_POINT_DATA) {
            return null;
        }
        if (packet.getPayload() == null || packet.getPayload().length < 40) {
            return null;
        }

        //  0:u32    pwr_up_msec
        //  4:u64    utc_msec
        // 12:float  listening_sec
        // 16:float  sos_mps
        // 20:u32    ping_number
        // 24:u32    ping_hz
        // 28:float  pulse_sec
        // 32:u32    flags
        // 36:u16    num_points
        // 38:u16    reserved
        // 40:atof_t atof_point_data[num_points]
        //
        // atof_t:
        //  0:float angle
        //  4:float tof
        //  8:u32 reserved[2]

        ByteBuffer buf = ByteBuffer
                .wrap(packet.getPayload())
                .order(ByteOrder.LITTLE_ENDIAN);

        buf.position(16);
        float speedOfSound = buf.getFloat(); // mps

        buf.position(36);
        int numPoints = buf.getShort() & 0xffff;
        if (numPoints == 0 || packet.getPayload().length < 40 + numPoints * 16) {
            return null;
        }

        double minAngle = Double.POSITIVE_INFINITY;
        Double depth = null;
        for (int i = 0; i < numPoints; i++) {
            buf.position(40 + i * 16);

            float angle = buf.getFloat(); // rad, positive to port
            if (Math.abs(angle) < minAngle) {
                minAngle = Math.abs(angle);

                float timeOfFlight = buf.getFloat(); // s
                double distance = 0.5 * speedOfSound * timeOfFlight;
                depth = distance * Math.cos(angle);
            }
        }
        return depth;
    }
}
