package com.ugcs.geohammer.format.svlog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Strings;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.PositionSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.TimeSentence;
import net.sf.marineapi.nmea.util.Position;
import net.sf.marineapi.nmea.util.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class SvlogParser {

    private static final Logger log = LoggerFactory.getLogger(SvlogParser.class);

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

    public Double parseDepth(SvlogPacket packet) {
        if (packet == null) {
            return null;
        }
        if (packet.getPacketId() == SvlogPacketId.OMNISCAN_MONO_PROFILE) {
            OmniscanParser omniscanParser = new OmniscanParser();
            return omniscanParser.getDepth(packet);
        }
        if (packet.getPacketId() == SvlogPacketId.SURVEYOR_ATOF_POINT_DATA) {
            SurveyorParser surveyorParser = new SurveyorParser();
            return surveyorParser.getDepth(packet);
        }
        return null;
    }

    public JsonObject parseJson(SvlogPacket packet) {
        if (packet == null) {
            return null;
        }
        if (packet.getPacketId() == SvlogPacketId.JSON_WRAPPER) {
            ByteArrayInputStream in = new ByteArrayInputStream(packet.getPayload());
            try (Reader r = new InputStreamReader(in, StandardCharsets.US_ASCII)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            } catch (IOException e) {
                log.warn("Cannot parse json packet", e);
                return null;
            }
        }
        return null;
    }

    public boolean isMetaPacket(SvlogPacket packet) {
        if (packet == null) {
            return false;
        }
        if (packet.getPacketId() == SvlogPacketId.DEVICE_INFORMATION) {
            return true;
        }
        if (packet.getPacketId() == SvlogPacketId.JSON_WRAPPER) {
            JsonObject json = parseJson(packet);
            // session packet
            return json != null && json.has("session_id");
        }
        return false;
    }
}
