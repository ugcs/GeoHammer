package com.ugcs.geohammer.util;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

public class PrintableFilter extends FilterReader {

    private long numRejected;

    private long markNumRejected;

    public PrintableFilter(Reader in) {
        super(Check.notNull(in));
    }

    public long numRejected() {
        return numRejected;
    }

    @Override
    public int read() throws IOException {
        int c;
        while ((c = in.read()) != -1) {
            if (Text.isPrintable((char) c)) {
                return c;
            }
            numRejected++;
        }
        return -1;
    }

    @Override
    public int read(char[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        int numKept = 0;
        while (numKept < length && (numKept == 0 || in.ready())) {
            int chunkOffset = offset + numKept;
            int numRead = in.read(buffer, chunkOffset, length - numKept);
            if (numRead == -1) {
                return numKept > 0 ? numKept : -1;
            }
            for (int i = 0; i < numRead; i++) {
                char c = buffer[chunkOffset + i];
                if (Text.isPrintable(c)) {
                    buffer[offset + numKept++] = c;
                } else {
                    numRejected++;
                }
            }
        }
        return numKept;
    }

    @Override
    public long skip(long n) throws IOException {
        long numSkipped = 0;
        while (numSkipped < n && read() != -1) {
            numSkipped++;
        }
        return numSkipped;
    }

    @Override
    public void mark(int readAheadLimit) throws IOException {
        in.mark(readAheadLimit);
        markNumRejected = numRejected;
    }

    @Override
    public void reset() throws IOException {
        in.reset();
        numRejected = markNumRejected;
    }
}
