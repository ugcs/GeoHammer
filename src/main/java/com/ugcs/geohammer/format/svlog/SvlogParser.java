package com.ugcs.geohammer.format.svlog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Strings;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.HeadingSentence;
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

    private final OmniscanParser omniscanParser = new OmniscanParser();

    private final SurveyorParser surveyorParser = new SurveyorParser();

    public void parseValues(SvlogPacket packet, SonarState sonarState) {
        LatLon latLon = parseLocation(packet);
        if (latLon != null) {
            sonarState.setLatLon(latLon);
        }
        Double altitude = parseAltitude(packet);
        if (altitude != null) {
            sonarState.setAltitude(altitude);
        }
        Instant timestamp = parseTime(packet);
        if (timestamp != null) {
            sonarState.setTimestamp(timestamp);
        }
        Double depth = parseDepth(packet);
        if (depth != null) {
            sonarState.setDepth(depth);
        }
        Float vehicleHeading = omniscanParser.getVehicleHeading(packet);
        if (vehicleHeading != null) {
            sonarState.setVehicleHeading(vehicleHeading);
        }
        Float transducerHeading = omniscanParser.getTransducerHeading(packet);
        if (transducerHeading != null) {
            sonarState.setTransducerHeading(transducerHeading);
        }
        Double heading = parseHeading(packet);
        if (heading != null) {
            sonarState.setHeading(heading);
        }
        SurveyorParser.Attitude attitude = surveyorParser.getAttitude(packet);
        if (attitude != null) {
            sonarState.setPitch(attitude.pitch());
            sonarState.setRoll(attitude.roll());
        }
        SurveyorParser.WaterStats waterStats = surveyorParser.getWaterStats(packet);
        if (waterStats != null) {
            sonarState.setTemperature(waterStats.temperature());
            sonarState.setPressure(waterStats.pressure());
        }
    }

    public static String stripNmeaChecksum(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return s;
        }
        int k = s.indexOf("*");
        return k != -1 ? s.substring(0, k) : s;
    }

    private Sentence parseNmeaSentence(SvlogPacket packet) {
        if (packet == null) {
            return null;
        }
        SentenceFactory sf = SentenceFactory.getInstance();
        if (packet.getPacketId() == SvlogPacketId.NMEA_WRAPPER) {
            String nmea = new String(packet.getPayload(), StandardCharsets.US_ASCII);
            nmea = stripNmeaChecksum(nmea);
            return sf.createParser(nmea);
        }
        return null;
    }

    public LatLon parseLocation(SvlogPacket packet) {
        Sentence sentence = parseNmeaSentence(packet);
        if (sentence instanceof PositionSentence positionSentence) {
            Position position = positionSentence.getPosition();
            return new LatLon(position.getLatitude(), position.getLongitude());
        }
        return null;
    }

    public Double parseAltitude(SvlogPacket packet) {
        Sentence sentence = parseNmeaSentence(packet);
        if (sentence instanceof PositionSentence positionSentence) {
            Position position = positionSentence.getPosition();
            return position.getAltitude();
        }
        return null;
    }

    public Double parseHeading(SvlogPacket packet) {
        Sentence sentence = parseNmeaSentence(packet);
        if (sentence instanceof HeadingSentence headingSentence) {
            return headingSentence.getHeading();
        }
        return null;
    }

    public Instant parseTime(SvlogPacket packet) {
        Sentence sentence = parseNmeaSentence(packet);
        if (sentence instanceof TimeSentence timeSentence) {
            Time time = timeSentence.getTime();
            return time != null ? time.toDate(new Date()).toInstant() : null;
        }
        return null;
    }

    public Double parseDepth(SvlogPacket packet) {
        if (packet == null) {
            return null;
        }
        if (packet.getPacketId() == SvlogPacketId.OMNISCAN_MONO_PROFILE) {
            return omniscanParser.getDepth(packet);
        }
        if (packet.getPacketId() == SvlogPacketId.SURVEYOR_ATOF_POINT_DATA) {
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
