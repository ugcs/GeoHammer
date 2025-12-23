package com.ugcs.geohammer.format.svlog;

public final class SvlogPacketId {

    public static final int NOP = 0;

    public static final int ACK = 1;

    public static final int NACK = 2;

    public static final int ASCII_TEXT = 3;

    public static final int DEVICE_INFORMATION = 4;

    public static final int GENERAL_REQUEST = 6;

    public static final int JSON_WRAPPER = 10;

    public static final int NMEA_WRAPPER = 109;

    public static final int MAVLINK_WRAPPER = 150;

    public static final int SYNC_CHANNEL_NUMBER = 169;

    public static final int SET_SYNC_CHANNEL_NUMBER = 170;

    public static final int OMNISCAN_MONO_PROFILE = 2198;

    public static final int SURVEYOR_WATER_STATS = 118;

    public static final int SURVEYOR_ATTITUDE_REPORT = 504;

    public static final int SURVEYOR_ATOF_POINT_DATA = 3012;

    private SvlogPacketId() {
    }
}
