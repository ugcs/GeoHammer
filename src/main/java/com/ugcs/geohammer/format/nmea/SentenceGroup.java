package com.ugcs.geohammer.format.nmea;

import net.sf.marineapi.nmea.sentence.Sentence;

import java.util.List;

public record SentenceGroup(List<Sentence> sentences) {
}
