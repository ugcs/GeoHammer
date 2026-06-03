package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Strings;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.DateSentence;
import net.sf.marineapi.nmea.sentence.HeadingSentence;
import net.sf.marineapi.nmea.sentence.PositionSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.TimeSentence;
import net.sf.marineapi.nmea.util.Date;
import net.sf.marineapi.nmea.util.Measurement;
import net.sf.marineapi.nmea.util.Position;
import net.sf.marineapi.nmea.util.Time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class NmeaParser {

    private static final SentenceFactory sf = SentenceFactory.getInstance();

    public static String stripNmeaChecksum(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return s;
        }
        int k = s.indexOf("*");
        return k != -1 ? s.substring(0, k) : s;
    }

    public Sentence parseSentence(String nmea) {
        if (nmea == null) {
            return null;
        }
        nmea = stripNmeaChecksum(nmea);
        return sf.createParser(nmea);
    }

    public LatLon parseLocation(Sentence sentence) {
        if (sentence instanceof PositionSentence positionSentence) {
            return parseLocation(positionSentence.getPosition());
        }
        return null;
    }

    public LatLon parseLocation(Position position) {
        return new LatLon(position.getLatitude(), position.getLongitude());
    }

    public Double parseAltitude(Sentence sentence) {
        if (sentence instanceof PositionSentence positionSentence) {
            Position position = positionSentence.getPosition();
            return position.getAltitude();
        }
        return null;
    }

    public Double parseHeading(Sentence sentence) {
        if (sentence instanceof HeadingSentence headingSentence) {
            return headingSentence.getHeading();
        }
        return null;
    }

    public Instant parseTime(Sentence sentence) {
        Date date = null;
        if (sentence instanceof DateSentence dateSentence) {
            date = dateSentence.getDate();
        }
        Time time = null;
        if (sentence instanceof TimeSentence timeSentence) {
            time = timeSentence.getTime();
        }
        return parseTime(date, time);
    }

    public Instant parseTime(Date date, Time time) {
        if (date == null || time == null) {
            return null;
        }
        long seconds = (long)time.getSeconds();
        long nanos = Math.round((time.getSeconds() - seconds) * 1_000_000_000);
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .plus(Duration.ofHours(time.getHour()))
                .plus(Duration.ofMinutes(time.getMinutes()))
                .plusSeconds((long)time.getSeconds())
                .plusNanos(nanos);
    }

    public Double parseValue(Measurement measurement) {
        if (measurement == null) {
            return null;
        }
        try {
            return measurement.getValue();
        } catch (Exception e) {
            // ignore, guards against null value unboxing
        }
        return null;
    }
}
