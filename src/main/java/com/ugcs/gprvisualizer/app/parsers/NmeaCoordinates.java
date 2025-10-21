package com.ugcs.gprvisualizer.app.parsers;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

import com.ugcs.gprvisualizer.app.parsers.exceptions.UnknownNmeaTypeException;

public class NmeaCoordinates extends GeoCoordinates {

    public static final String North = "N";
    public static final String South = "S";
    public static final String East = "E";
    public static final String West = "W";

    private String NMEAType;
    private String UTCPosition;
    private String positionStatus;
    private String northOrSouth;
    private String eastOrWest;
    private String NMEALatitude;
    private String NMEALongitude;
    private String speed;
    private String trakeMade;
    private String UTDate;
    private String magneticVariationDegrees;
    private String varDir;
    private String modeInd;

    //private Locale format;

    private NumberFormat format;

    public NmeaCoordinates(NumberFormat format) {
        this.format = format;
    }

    public NmeaCoordinates(NumberFormat format, String NMEALatitude, String NMEALongitude, String northOrSouth, String eastOrWest) {
        this(format);
        this.NMEALatitude = NMEALatitude;
        this.NMEALongitude = NMEALongitude;
        this.northOrSouth = northOrSouth;
        this.eastOrWest = eastOrWest;
    }

    public String getNMEAType() {
        return NMEAType;
    }

    public void setNMEAType(String NMEAType) {
        this.NMEAType = NMEAType;
    }

    public String getUTCPosition() {
        return UTCPosition;
    }

    public void setUTCPosition(String UTCPosition) {
        this.UTCPosition = UTCPosition;
    }

    public String getPositionStatus() {
        return positionStatus;
    }

    public void setPositionStatus(String positionStatus) {
        this.positionStatus = positionStatus;
    }

    public String getNorthOrSouth() {
        return northOrSouth;
    }

    public void setNorthOrSouth(String northOrSouth) {
        this.northOrSouth = northOrSouth;
    }

    public String getEastOrWest() {
        return eastOrWest;
    }

    public void setEastOrWest(String eastOrWest) {
        this.eastOrWest = eastOrWest;
    }

    public String getNMEALatitude() {
        return NMEALatitude;
    }

    public void setNMEALatitude(String NMEALatitude) {
        this.NMEALatitude = NMEALatitude;
    }

    public String getNMEALongitude() {
        return NMEALongitude;
    }

    public void setNMEALongitude(String NMEALongitude) {
        this.NMEALongitude = NMEALongitude;
    }

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String getTrakeMade() {
        return trakeMade;
    }

    public void setTrakeMade(String trakeMade) {
        this.trakeMade = trakeMade;
    }

    public String getUTDate() {
        return UTDate;
    }

    public void setUTDate(String UTDate) {
        this.UTDate = UTDate;
    }

    public String getMagneticVariationDegrees() {
        return magneticVariationDegrees;
    }

    public void setMagneticVariationDegrees(String magneticVariationDegrees) {
        this.magneticVariationDegrees = magneticVariationDegrees;
    }

    public String getVarDir() {
        return varDir;
    }

    public void setVarDir(String varDir) {
        this.varDir = varDir;
    }

    public String getModeInd() {
        return modeInd;
    }

    public void setModeInd(String modeInd) {
        this.modeInd = modeInd;
    }

    /*public Locale getFormat() {
        return format;
    }

    public void setFormat(Locale format) {
        this.format = format;
    }*/

    public static String convertToNMEACoordinates(double coordinate) {
        return String.format("%.4f", Math.abs(Math.floor(coordinate) * 100 + (coordinate - Math.floor(coordinate)) * 60));
    }

    public double convertToGoogleCoordinates(String coordinate, String cardinalDirection) {
        String decimalSeparator = format != null ? String.valueOf(DecimalFormatSymbols.getInstance().getDecimalSeparator()) : ".";
        int digitCount = Character.toString(coordinate.charAt(4)).equals(decimalSeparator) ? 2 : 3;

        char[] integerPart = coordinate.substring(0, digitCount).toCharArray();
        coordinate = coordinate.substring(digitCount);
        double result = Integer.parseInt(new String(integerPart)) + Double.parseDouble(coordinate) / 60;
        if (cardinalDirection.equals(West) || cardinalDirection.equals(South))
            result = -result;
        return result;
    }

    public int calculateCheckSum() {
        String line = NMEAType + UTCPosition + positionStatus + NMEALatitude + northOrSouth + NMEALongitude + eastOrWest + speed + trakeMade + UTDate + magneticVariationDegrees + varDir + modeInd;
        int checkSum = 0;
        for (int i = 0; i < line.length(); i++)
            checkSum ^= (byte) line.charAt(i);
        return checkSum;
    }

    public String createNMEAMessage(String checksum) {
        return " $" + NMEAType + "," + UTCPosition + "," + positionStatus + "," + NMEALatitude + "," + northOrSouth + "," + NMEALongitude + "," + eastOrWest + "," + speed + "," + trakeMade + "," + UTDate + "," + magneticVariationDegrees + "," +
                varDir + "," + modeInd + "*" + checksum;
    }

    public void parseNMEAMessage(String message) {
                String[] data = message.split(",");
                if (data[0].contains("$"))
                    data[0] = data[0].replace("$", "").trim();
                if (!data[0].equals("GPRMC") && !data[0].equals("GNRMC"))
                    throw new UnknownNmeaTypeException("Unsupported NMEA type");
                NMEAType = data[0].substring(0, data[0].length());
                UTCPosition = data[1];
                positionStatus = data[2];
                NMEALatitude = data[3];
                northOrSouth = data[4];
                NMEALongitude = data[5];
                eastOrWest = data[6];
                speed = data[7];
                trakeMade = data[8];
                UTDate = data[9];
        magneticVariationDegrees = data[10];
        varDir = data[11];
        modeInd = Character.toString(data[12].charAt(0));
        setLatitude(convertToGoogleCoordinates(NMEALatitude, northOrSouth));
        setLongitude(convertToGoogleCoordinates(NMEALongitude, eastOrWest));
    }
    
    public void setNewNmeaCoordinates() {
        NMEALatitude = convertToNMEACoordinates(getLatitude());
        NMEALongitude = convertToNMEACoordinates(getLongitude());
        northOrSouth = getLatitude() > 0 ? North : South;
        eastOrWest = getLongitude() > 0 ? East : West;
    }


}