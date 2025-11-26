package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Strings;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.PositionSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.TimeSentence;
import net.sf.marineapi.nmea.util.Position;
import net.sf.marineapi.nmea.util.Time;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class SvlogParser {

    public static String stripNmeaChecksum(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return s;
        }
        int k = s.indexOf("*");
        return k != -1 ? s.substring(0, k) : s;
    }

    public LatLon parseLocation(SvlogPacket packet) {
        if (packet == null) {
            return null;
        }
        SentenceFactory sf = SentenceFactory.getInstance();
        if (packet.getPacketId() == SvlogPacketId.NMEA_WRAPPER) {
            String nmea = new String(packet.getPayload(), StandardCharsets.US_ASCII);
            nmea = stripNmeaChecksum(nmea);

            Sentence sentence = sf.createParser(nmea);
            if (sentence instanceof PositionSentence positionSentence) {
                Position position = positionSentence.getPosition();
                return new LatLon(position.getLatitude(), position.getLongitude());
            }
        }
        return null;
    }

    public Instant parseTime(SvlogPacket packet) {
        if (packet == null) {
            return null;
        }
        SentenceFactory sf = SentenceFactory.getInstance();
        if (packet.getPacketId() == SvlogPacketId.NMEA_WRAPPER) {
            String nmea = new String(packet.getPayload(), StandardCharsets.US_ASCII);
            nmea = stripNmeaChecksum(nmea);

            Sentence sentence = sf.createParser(nmea);
            if (sentence instanceof TimeSentence timeSentence) {
                Time time = timeSentence.getTime();
                return time != null ? time.toDate(new Date()).toInstant() : null;
            }
        }
        return null;
    }
}
