package com.ugcs.geohammer.format.gpr.segy;

import com.github.thecoldwine.sigrun.serialization.BinaryHeaderFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SegyByteOrder {

    // SEG-Y Rev 2 byte-order: 3297-3300 (offset 96 within the binary header)
    private static final int BYTE_ORDER_POS = 96;

    private static final int BYTE_ORDER_BIG_ENDIAN = 0x01020304;

    private static final int BYTE_ORDER_LITTLE_ENDIAN = 0x04030201;

    private SegyByteOrder() {
    }

    public static ByteOrder detect(byte[] header, BinaryHeaderFormat format) {
        ByteOrder byteOrder = fromByteOrderConstant(header);
        if (byteOrder == null) {
            byteOrder = fromDataSampleCode(header, format);
        }
        if (byteOrder == null) {
            byteOrder = ByteOrder.BIG_ENDIAN;
        }
        return byteOrder;
    }

    private static ByteOrder fromByteOrderConstant(byte[] header) {
        if (header == null || header.length < BYTE_ORDER_POS + 4) {
            return null;
        }
        int value = ByteBuffer.wrap(header, BYTE_ORDER_POS, 4)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
        if (value == BYTE_ORDER_BIG_ENDIAN) {
            return ByteOrder.BIG_ENDIAN;
        }
        if (value == BYTE_ORDER_LITTLE_ENDIAN) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        return null;
    }

    // heuristic: only one byte order yields a valid data sample format code
    private static ByteOrder fromDataSampleCode(byte[] header, BinaryHeaderFormat format) {
        if (format == null || format.dataSampleCodeFormat == null) {
            return null;
        }
        int offset = format.dataSampleCodeFormat.posStart;
        if (header == null || header.length < offset + 2) {
            return null;
        }
        // read in big endian
        short dataSampleCode = ByteBuffer.wrap(header, offset, 2)
                .order(ByteOrder.BIG_ENDIAN)
                .getShort();
        if (dataSampleCode >= 1 && dataSampleCode <= 16) {
            return ByteOrder.BIG_ENDIAN;
        }
        // reverse byte order
        dataSampleCode = Short.reverseBytes(dataSampleCode);
        if (dataSampleCode >= 1 && dataSampleCode <= 16) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        return null;
    }
}
