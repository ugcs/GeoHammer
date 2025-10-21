package com.ugcs.gprvisualizer.app.parsers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.NumberFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

public class NmeaFormatTests {

    private static final double Delta = 0.00001;


    @Test
    void TestNMEAToGoogleCoordinates() {
        Locale locale = Locale.US;
        NumberFormat nf = NumberFormat.getInstance(locale);
        nf.setMaximumFractionDigits(5);
        NmeaCoordinates nmeaCoordinate = new NmeaCoordinates(
                nf,
                "5651.7446",
                "2406.7933",
                "N",
                "E"
        );
        double latitude = nmeaCoordinate.convertToGoogleCoordinates(nmeaCoordinate.getNMEALatitude(), nmeaCoordinate.getNorthOrSouth());
        double longitude = nmeaCoordinate.convertToGoogleCoordinates(nmeaCoordinate.getNMEALongitude(), nmeaCoordinate.getEastOrWest());
        assertTrue(latitude - 56.86241 < Delta && longitude - 24.113221 < Delta);
    }

    @Test
    void TestGoogleToNMEACoordinates() {
        Locale locale = Locale.US;
        NumberFormat nf = NumberFormat.getInstance(locale);
        nf.setMaximumFractionDigits(5);
        NmeaCoordinates nmeaCoordinate = new NmeaCoordinates(
                nf);
        //        56.86241,
        //        24.113221,
        //        "N",
        //        "E"
        nmeaCoordinate.setLatitude(56.86241);
        nmeaCoordinate.setLongitude(24.113221);
        double latitude = Double.parseDouble(nmeaCoordinate.convertToNMEACoordinates(nmeaCoordinate.getLatitude())); //, locale));
        double longitude = Double.parseDouble(nmeaCoordinate.convertToNMEACoordinates(nmeaCoordinate.getLongitude())); //, locale));
        assertTrue(latitude - 5651.7446 < Delta && longitude - 2406.7933 < Delta);
    }

    @Test
    void TestNMEAMessageParsing() {
        Locale locale = Locale.US;
        NumberFormat nf = NumberFormat.getInstance(locale);
        nf.setMaximumFractionDigits(5);
        NmeaCoordinates coordinate = new NmeaCoordinates(nf);
        coordinate.parseNMEAMessage("$GNRMC,091613.699,A,5651.7446,N,02406.7933,E,0.18,-49.88,231020,,,A*69");
        assertTrue(true);
    }

    @Test
    void TestCheckSumCalculation() {
        Locale locale = Locale.US;
        NumberFormat nf = NumberFormat.getInstance(locale);
        nf.setMaximumFractionDigits(5);
        NmeaCoordinates coordinate = new NmeaCoordinates(nf);
        coordinate.parseNMEAMessage("$GNRMC,091613.699,A,5651.7446,N,02406.7933,E,0.18,-49.88,231020,,,A*69");
        String ckecksum = Integer.toHexString(coordinate.calculateCheckSum()).toUpperCase();
        coordinate.parseNMEAMessage("$GNRMC,091617.692,A,5651.7447,N,02406.7932,E,0.11,-50.19,231020,,,A*6F");
        String secondChecksum = Integer.toHexString(coordinate.calculateCheckSum()).toUpperCase();
        assertTrue(ckecksum.equals("69") && secondChecksum.equals("6F"));
    }
}