package com.ugcs.gprvisualizer.app.parcers;

import com.github.thecoldwine.sigrun.common.ext.LatLon;

import java.time.LocalDateTime;
import java.util.Date;

public class GeoCoordinates {

    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double timeInMs;
    private Integer traceNumber;
    private LocalDateTime dateTime;

    protected GeoCoordinates() {
    }

    public GeoCoordinates(double latitude, double longitude, Double altitude, Double timeInMs, int traceNumber,
            LocalDateTime dateTime) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.timeInMs = timeInMs;
        this.traceNumber = traceNumber;
        this.dateTime = dateTime;
    }

    public GeoCoordinates(LocalDateTime dateTime, double lat, double lon, Double alt, int traceNumber) {
        this(lat, lon, alt, null, traceNumber, dateTime);
    }

    public LatLon getLatLon() {
        return new LatLon(latitude, longitude);
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public Double getTimeInMs() {
        return timeInMs;
    }

    public void setTimeInMs(double timeInMs) {
        this.timeInMs = timeInMs;
    }

    public Integer getTraceNumber() {
        return traceNumber;
    }

    public void setTraceNumber(int traceNumber) {
        this.traceNumber = traceNumber;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }
}
