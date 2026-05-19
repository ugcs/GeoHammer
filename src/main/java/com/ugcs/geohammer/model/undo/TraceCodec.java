package com.ugcs.geohammer.model.undo;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Nulls;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TraceCodec {

    private TraceCodec() {
    }

    private static void writeBytes(DataOutput out, byte[] bytes) throws IOException {
        out.writeBoolean(bytes != null);
        if (bytes != null) {
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }

    private static byte[] readBytes(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        return bytes;
    }

    private static void writeFloats(DataOutput out, float[] values) throws IOException {
        out.writeInt(values.length);
        for (float value : values) {
            out.writeFloat(value);
        }
    }

    private static float[] readFloats(DataInput in) throws IOException {
        float[] values = new float[in.readInt()];
        for (int i = 0; i < values.length; i++) {
            values[i] = in.readFloat();
        }
        return values;
    }

    private static void writeLatLon(DataOutput out, LatLon latLon) throws IOException {
        out.writeBoolean(latLon != null);
        if (latLon != null) {
            out.writeDouble(latLon.getLatDgr());
            out.writeDouble(latLon.getLonDgr());
        }
    }

    private static LatLon readLatLon(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return new LatLon(in.readDouble(), in.readDouble());
    }

    private static void writeInstant(DataOutput out, Instant instant) throws IOException {
        long timestamp = instant != null ? instant.toEpochMilli() : -1;
        out.writeLong(timestamp);
    }

    private static Instant readInstant(DataInput in) throws IOException {
        long timestamp = in.readLong();
        return timestamp != -1 ? Instant.ofEpochMilli(timestamp) : null;
    }

    private static void writeIndexRange(DataOutput out, IndexRange range) throws IOException {
        out.writeBoolean(range != null);
        if (range != null) {
            out.writeInt(range.from());
            out.writeInt(range.to());
        }
    }

    private static IndexRange readIndexRange(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return new IndexRange(in.readInt(), in.readInt());
    }

    private static void writeTrace(DataOutput out, Trace trace) throws IOException {
        writeBytes(out, trace.getBinHeader());
        writeFloats(out, trace.getFileSamples());
        writeLatLon(out, trace.getLatLon());
        writeInstant(out, trace.getDateTime());
        writeIndexRange(out, trace.getSampleRange());
        out.writeDouble(trace.getPrevDist());
        out.writeInt(trace.getMaxIndex());
        out.writeFloat(trace.getReceiverAltitude());
        out.writeBoolean(trace.isMarked());
    }

    private static Trace readTrace(DataInput in) throws IOException {
        byte[] binHeader = readBytes(in);
        TraceHeader header = binHeader != null
                ? GprFile.traceHeaderReader.read(binHeader)
                : null;
        float[] samples = readFloats(in);
        LatLon latLon = readLatLon(in);
        Instant dateTime = readInstant(in);

        Trace trace = new Trace(binHeader, header, samples, latLon, dateTime);
        trace.setSampleRange(readIndexRange(in));
        trace.setPrevDist(in.readDouble());
        trace.setMaxIndex(in.readInt());
        trace.setReceiverAltitude(in.readFloat());
        trace.setMarked(in.readBoolean());
        return trace;
    }

    public static void write(DataOutput out, List<Trace> traces) throws IOException {
        traces = Nulls.toEmpty(traces);
        out.writeInt(traces.size());
        for (Trace trace : traces) {
            writeTrace(out, trace);
        }
    }

    public static List<Trace> read(DataInput in) throws IOException {
        int numTraces = in.readInt();
        List<Trace> traces = new ArrayList<>(numTraces);
        for (int i = 0; i < numTraces; i++) {
            Trace trace = readTrace(in);
            trace.setIndex(i);
            traces.add(trace);
        }
        return traces;
    }
}
