package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Strings;
import net.sf.marineapi.nmea.parser.SentenceFactory;
import net.sf.marineapi.nmea.sentence.DateSentence;
import net.sf.marineapi.nmea.sentence.GGASentence;
import net.sf.marineapi.nmea.sentence.HeadingSentence;
import net.sf.marineapi.nmea.sentence.PositionSentence;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.SentenceValidator;
import net.sf.marineapi.nmea.sentence.TimeSentence;
import net.sf.marineapi.nmea.util.Date;
import net.sf.marineapi.nmea.util.GpsFixQuality;
import net.sf.marineapi.nmea.util.Measurement;
import net.sf.marineapi.nmea.util.Position;
import net.sf.marineapi.nmea.util.Time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

public class NmeaParser {

    private static final SentenceFactory sf = SentenceFactory.getInstance();

    private static final Duration HALF_DAY = Duration.ofHours(12);

    public static boolean isNmeaSentence(String s) {
        return SentenceValidator.isSentence(s);
    }

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

    public static <T> T parseOrNull(Supplier<T> parse) {
        try {
            return parse.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public LatLon parseLocation(Sentence sentence) {
        if (sentence instanceof PositionSentence positionSentence) {
            return parseOrNull(() -> parseLocation(positionSentence.getPosition()));
        }
        return null;
    }

    public LatLon parseLocation(Position position) {
        return new LatLon(position.getLatitude(), position.getLongitude());
    }

    public Double parseAltitude(Sentence sentence) {
        if (sentence instanceof PositionSentence positionSentence) {
            return parseOrNull(() -> positionSentence.getPosition().getAltitude());
        }
        return null;
    }

    public Double parseHeading(Sentence sentence) {
        if (sentence instanceof HeadingSentence headingSentence) {
            return parseOrNull(headingSentence::getHeading);
        }
        return null;
    }

    public Integer parseFixQuality(Sentence sentence) {
        if (sentence instanceof GGASentence ggaSentence) {
            GpsFixQuality fixQuality = parseOrNull(ggaSentence::getFixQuality);
            return fixQuality != null ? fixQuality.toInt() : null;
        }
        return null;
    }

    public Instant parseTime(Sentence sentence) {
        if (sentence instanceof DateSentence dateSentence
                && sentence instanceof TimeSentence timeSentence) {
            return parseOrNull(() -> parseTime(dateSentence.getDate(), timeSentence.getTime()));
        }
        return null;
    }

    // nearest moment to the reference with the time of day of the sentence
    public Instant parseTimeOfDay(Sentence sentence, Instant reference) {
        if (reference == null || !(sentence instanceof TimeSentence timeSentence)) {
            return null;
        }
        Time timeOfDay = parseOrNull(timeSentence::getTime);
        if (timeOfDay == null) {
            return null;
        }
        Instant time = reference.truncatedTo(ChronoUnit.DAYS)
                .plusMillis(timeOfDay.getMilliseconds());
        // near midnight the time of day belongs to the adjacent day
        if (time.isBefore(reference.minus(HALF_DAY))) {
            return time.plus(Duration.ofDays(1));
        }
        if (time.isAfter(reference.plus(HALF_DAY))) {
            return time.minus(Duration.ofDays(1));
        }
        return time;
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
        // guard against null value unboxing
        return parseOrNull(measurement::getValue);
    }
}
