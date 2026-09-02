package com.ugcs.geohammer.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

public class TextContentProbe implements FileProbe {

    private static final int SAMPLE_SIZE = 64 * 1024;

    private static final List<byte[]> BINARY_SIGNATURES = List.of(
            toBytes(0x7f, 'E', 'L', 'F'),
            toBytes('P','K', 3, 4),
            toBytes('R','A', 'R', '!'),
            toBytes(0x1f, 0x8b, 0x08), // gzip
            toBytes(0x89,'P','N','G'),
            toBytes(0xff, 0xd8, 0xff), // jpeg
            toBytes(0x49, 0x49, 0x2a, 0x00), // tiff little endian
            toBytes(0x4d, 0x4d, 0x00, 0x2a), // tiff big endian
            toBytes('%','P','D','F')
    );

    private static byte[] toBytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    @Override
    public boolean matches(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] sample = in.readNBytes(SAMPLE_SIZE);
            return isProbablyText(sample);
        } catch (IOException ignore) {
            return false;
        }
    }

    private static boolean isProbablyText(byte[] bytes) {
        if (bytes.length == 0) {
            return true;
        }
        if (startsWithAny(bytes, BINARY_SIGNATURES)) {
            return false;
        }
        // utf-16 bom is rejected here as well
        for (byte b : bytes) {
            if (b == 0) {
                return false;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = CharBuffer.allocate(bytes.length);
        // endOfInput is false: a character cut by the sample boundary
        // underflows instead of failing the whole decode
        CoderResult result = decoder.decode(ByteBuffer.wrap(bytes), chars, false);
        if (result.isError()) {
            return false;
        }
        chars.flip();
        int numChars = chars.remaining();
        int numUnprintable = 0;
        for (int i = 0; i < numChars; i++) {
            char c = chars.get(i);
            if (!Text.isPrintable(c)) {
                numUnprintable++;
            }
        }
        return numUnprintable <= Math.max(1, numChars / 100);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAny(byte[] bytes, Collection<byte[]> prefixes) {
        for (byte[] prefix : prefixes) {
            if (startsWith(bytes, prefix)) {
                return true;
            }
        }
        return false;
    }
}
