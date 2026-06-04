package com.ugcs.geohammer.format.nmea;

import com.ugcs.geohammer.format.meta.TraceGeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.util.Strings;
import net.sf.marineapi.nmea.sentence.Sentence;
import net.sf.marineapi.nmea.sentence.TalkerId;

public final class NmeaSchema {

    private NmeaSchema() {
    }

    public static ColumnSchema createSchema() {
        return ColumnSchema.copy(TraceGeoData.SCHEMA);
    }

    public static Column createColumn(String header, String unit) {
        return new Column(header)
                .withUnit(unit)
                .withDisplay(true)
                .withReadOnly(true);
    }

    public static String composeHeader(Sentence sentence, String name) {
        if (Strings.isNullOrEmpty(name)) {
            return Strings.empty();
        }
        if (sentence == null) {
            return name;
        }
        TalkerId talker = sentence.getTalkerId();
        String prefix = talker != null ? talker.toString() : Strings.empty();
        prefix += Strings.emptyToNull(sentence.getSentenceId());
        return !prefix.isEmpty() ? prefix + ":" + name : name;
    }
}
