package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.util.Check;
import net.sf.marineapi.nmea.sentence.Sentence;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class NmeaReader implements Closeable {

    private final NmeaParser nmeaParser = new NmeaParser();

    private final BufferedReader reader;

    public NmeaReader(File file) throws IOException {
        Check.notNull(file);
        reader = Files.newBufferedReader(file.toPath(), StandardCharsets.US_ASCII);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public Sentence readSentence() throws IOException {
        String nmea = reader.readLine();
        if (nmea == null) {
            return null;
        }
        return nmeaParser.parseSentence(nmea);
    }
}
