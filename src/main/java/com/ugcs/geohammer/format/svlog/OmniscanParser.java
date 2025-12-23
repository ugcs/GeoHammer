package com.ugcs.geohammer.format.svlog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OmniscanParser {

    public Double getDepth(SvlogPacket packet) {
        if (packet.getPacketId() != SvlogPacketId.OMNISCAN_MONO_PROFILE) {
            return null;
        }
        if (packet.getPayload() == null || packet.getPayload().length < 52) {
            return null;
        }

        //  0:u32   ping_number
        //  4:u32   start_mm
        //  8:u32   length_mm
        // 12:u32   timestamp_ms
        // 16:u32   ping_hz
        // 20:u16   gain_index
        // 22:u16   num_results
        // 24:u16   sos_dmps
        // 26:u8    channel_number
        // 27:u8    reserved
        // 28:float pulse_duration_sec
        // 32:float analog_gain
        // 36:float max_pwr_db
        // 40:float min_pwr_db
        // 44:float transducer_heading_deg
        // 48:float vehicle_heading_deg
        // 52:u16   pwr_results[num_results]

        ByteBuffer buf = ByteBuffer
                .wrap(packet.getPayload())
                .order(ByteOrder.LITTLE_ENDIAN);

        buf.position(4);
        long start = buf.getInt() & 0xffffffffL; // mm
        long length = buf.getInt() & 0xffffffffL; // mm

        buf.position(22);
        int numResults = buf.getShort() & 0xffff;
        if (numResults == 0 || packet.getPayload().length < 52 + numResults * 2) {
            return null;
        }

        buf.position(32);
        float analogGain = buf.getFloat();
        float maxPowerDb = buf.getFloat();
        float minPowerDb = buf.getFloat();
        double powerDbFactor = (maxPowerDb - minPowerDb) / ((1 << 16) - 1);

        double[] prefixSum =  new double[numResults];

        buf.position(52);
        for (int i = 0; i < numResults; i++) {
            int packed =  buf.getShort() & 0xffff;
            double powerDb = minPowerDb + powerDbFactor * packed;
            double power = Math.pow(10, 0.1 * powerDb) / (analogGain * analogGain);
            prefixSum[i] = Math.log(power) + (i > 0 ? prefixSum[i - 1] : 0);
        }

        double maxScore = Double.NEGATIVE_INFINITY;
        int maxIndex = 0;

        int scanFrom = (int)Math.ceil(numResults / 32.0);
        int scanTo = (int)Math.floor(numResults * 15.0 / 16.0);
        int meanFrom = (int) Math.ceil(numResults / 64.0);

        for (int i = scanFrom; i < scanTo; i++) {
            int meanTo = Math.min(numResults - 1, i + numResults / 8);
            double meanBefore = (prefixSum[i] - prefixSum[meanFrom]) / (i - meanFrom);
            double meanAfter = (prefixSum[meanTo] - prefixSum[i]) / (meanTo - i);
            double score = meanAfter - meanBefore;
            if (score > maxScore) {
                maxScore = score;
                maxIndex = i;
            }
        }

        double depth = start + (length * maxIndex / (double) numResults);
        return depth * 1e-3; // to meters
    }

    public Float getTransducerHeading(SvlogPacket packet) {
        if (packet.getPacketId() != SvlogPacketId.OMNISCAN_MONO_PROFILE) {
            return null;
        }
        if (packet.getPayload() == null || packet.getPayload().length < 52) {
            return null;
        }

        ByteBuffer buf = ByteBuffer
                .wrap(packet.getPayload())
                .order(ByteOrder.LITTLE_ENDIAN);

        buf.position(44);
        return buf.getFloat();
    }

    public Float getVehicleHeading(SvlogPacket packet) {
        if (packet.getPacketId() != SvlogPacketId.OMNISCAN_MONO_PROFILE) {
            return null;
        }
        if (packet.getPayload() == null || packet.getPayload().length < 52) {
            return null;
        }

        ByteBuffer buf = ByteBuffer
                .wrap(packet.getPayload())
                .order(ByteOrder.LITTLE_ENDIAN);

        buf.position(48);
        return buf.getFloat();
    }
}
