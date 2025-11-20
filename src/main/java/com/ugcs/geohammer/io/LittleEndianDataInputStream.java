package com.ugcs.geohammer.io;

import java.io.DataInput;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LittleEndianDataInputStream extends FilterInputStream implements DataInput {

    public LittleEndianDataInputStream(InputStream in) {
        super(in);
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        int n = 0;
        while (n < len) {
            int bytesRead = in.read(b, off + n, len - n);
            if (bytesRead < 0) {
                throw new EOFException();
            }
            n += bytesRead;
        }
    }

    @Override
    public int skipBytes(int n) throws IOException {
        int len = n;
        n = 0;
        while (n < len) {
            int skipped = (int)in.skip(len - n);
            if (skipped <= 0) {
                break;
            }
            n += skipped;
        }
        return n;
    }

    @Override
    public boolean readBoolean() throws IOException {
        int a = in.read();
        if (a == -1) {
            throw new EOFException();
        }
        return a != 0;
    }

    @Override
    public byte readByte() throws IOException {
        int a = in.read();
        if (a == -1) {
            throw new EOFException();
        }
        return (byte)a;
    }

    @Override
    public int readUnsignedByte() throws IOException {
        int a = in.read();
        if (a == -1) {
            throw new EOFException();
        }
        return a;
    }

    @Override
    public short readShort() throws IOException {
        int a = in.read();
        int b = in.read();
        if ((a | b) < 0) {
            throw new EOFException();
        }
        return (short)((b << 8) | a);
    }

    @Override
    public int readUnsignedShort() throws IOException {
        int a = in.read();
        int b = in.read();
        if ((a | b) < 0) {
            throw new EOFException();
        }
        return (b << 8) | a;
    }

    @Override
    public char readChar() throws IOException {
        int a = in.read();
        int b = in.read();
        if ((a | b) < 0) {
            throw new EOFException();
        }
        return (char)((b << 8) | a);
    }

    @Override
    public int readInt() throws IOException {
        int a = in.read();
        int b = in.read();
        int c = in.read();
        int d = in.read();
        if ((a | b | c | d) < 0) {
            throw new EOFException();
        }
        return (d << 24) | (c << 16) | (b << 8) | a;
    }

    @Override
    public long readLong() throws IOException {
        int a = in.read();
        int b = in.read();
        int c = in.read();
        int d = in.read();
        int e = in.read();
        int f = in.read();
        int g = in.read();
        int h = in.read();
        if ((a | b | c | d | e | f | g | h) < 0) {
            throw new EOFException();
        }
        return ((long)h << 56) | ((long)g << 48) | ((long)f << 40) | ((long)e << 32)
                | ((long)d << 24) | (c << 16) | (b << 8) | a;
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readLine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() {
        throw new UnsupportedOperationException();
    }
}