package com.ugcs.gprvisualizer.app.parcers.segylog;

import java.io.IOException;
import java.util.List;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.Parser;
import com.ugcs.gprvisualizer.app.yaml.Template;

public class SegYLogParser extends Parser {
    
    private static final short TextBytesOffset = 3200;
    private static final short SamplesPerTraceOffset = 3222;
    private static final short SamplesFormatOffset = 3226;
    private static final short HeadersOffset = 3600;
    private static final short TraceNumberOffset = 0;
    private static final short AltitudeOffset = 40;
    private static final short ScalarOffset = 70;
    private static final short LongitudeOffset = 72;
    private static final short LatitudeOffset = 76;
    private static final short YearOffset = 156;
    private static final short DayOfYearOffset = 158;
    private static final short HourOffset = 160;
    private static final short MinuteOffset = 162;
    private static final short SecondOffset = 164;
    private static final short MSecondOffset = 168;
    private static final short LongitudeGprOffset = 182;
    private static final short LatitudeGprOffset = 190;
    private static final short TraceHeaderOffset = 240;
    private static final short SecondsInDegree = 3600;
    private static final String Gpr = "Georadar's settings information";
    private static final String EchoSounder = "Echosounder's settings information";
    private static final String Unknown = "Unknown";
    
    private String PayloadType;
    private short TracesLength;
    private int SampleFormatBytes;

    public SegYLogParser(Template template) {
        super(template);
    }

    @Override
    public List<GeoCoordinates> parse(String path) throws IOException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'parse'");
    }
}
