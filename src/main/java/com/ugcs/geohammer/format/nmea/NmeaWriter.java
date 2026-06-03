package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.util.Check;
import net.sf.marineapi.nmea.sentence.Sentence;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class NmeaWriter implements Closeable {

    private final BufferedWriter writer;

    public NmeaWriter(File file) throws IOException {
        Check.notNull(file);
        writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.US_ASCII);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    public void writeSentence(Sentence sentence) throws IOException {
        if (sentence == null) {
            return;
        }
        writer.write(sentence.toSentence());
        writer.newLine();
    }
}
