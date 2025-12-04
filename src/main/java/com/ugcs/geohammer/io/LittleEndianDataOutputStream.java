package com.ugcs.geohammer.io;

import java.io.DataOutput;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LittleEndianDataOutputStream extends FilterOutputStream implements DataOutput {
    public LittleEndianDataOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
        out.write(v ? 1 : 0);
    }

    @Override
    public void writeByte(int v) throws IOException {
        out.write(v);
    }

    @Override
    public void writeShort(int v) throws IOException {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
    }

    @Override
    public void writeChar(int v) throws IOException {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
    }

    @Override
    public void writeInt(int v) throws IOException {
        byte[] buf = new byte[4];
        buf[0] = (byte)(v & 0xff);
        buf[1] = (byte)((v >>> 8) & 0xff);
        buf[2] = (byte)((v >>> 16) & 0xff);
        buf[3] = (byte)((v >>> 24) & 0xff);
        out.write(buf);
    }

    @Override
    public void writeLong(long v) throws IOException {
        byte[] buf = new byte[8];
        buf[0] = (byte)(v & 0xff);
        buf[1] = (byte)((v >>> 8) & 0xff);
        buf[2] = (byte)((v >>> 16) & 0xff);
        buf[3] = (byte)((v >>> 24) & 0xff);
        buf[4] = (byte)((v >>> 32) & 0xff);
        buf[5] = (byte)((v >>> 40) & 0xff);
        buf[6] = (byte)((v >>> 48) & 0xff);
        buf[7] = (byte)((v >>> 56) & 0xff);
        out.write(buf);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    @Override
    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    @Override
    public void writeBytes(String s) throws IOException {
        int length = s.length();
        for (int i = 0; i < length; ++i)
            out.write((byte)s.charAt(i));
    }

    @Override
    public void writeChars(String s) throws IOException {
        int length = s.length();
        for (int i = 0; i < length; ++i) {
            int v = s.charAt(i);
            out.write(v & 0xff);
            out.write((v >>> 8) & 0xff);
        }
    }

    @Override
    public void writeUTF(String s) {
        throw new UnsupportedOperationException();
    }
}
