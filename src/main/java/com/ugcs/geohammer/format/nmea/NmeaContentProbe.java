package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.util.FileProbe;
import com.ugcs.geohammer.util.Strings;
import net.sf.marineapi.nmea.sentence.SentenceValidator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class NmeaContentProbe implements FileProbe {

    private static final int PROBE_BYTES = 8 * 1024;

    @Override
    public boolean matches(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }

        String text;
        try (InputStream in = new FileInputStream(file)) {
            // never pull more than the probe window, even for a
            // binary file with no line breaks
            byte[] head = in.readNBytes(PROBE_BYTES);
            text = new String(head, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            return false;
        }
        boolean truncated = text.length() == PROBE_BYTES
                && !text.endsWith("\n");

        String[] lines = text.split("\\r?\\n");
        // skip last line as it may be truncated
        int limit = truncated ? lines.length - 1 : lines.length;
        for (int i = 0; i < limit; i++) {
            String line = lines[i];
            if (Strings.isNullOrBlank(line)) {
                continue;
            }
            return SentenceValidator.isValid(line);
        }
        return false;
    }
}
