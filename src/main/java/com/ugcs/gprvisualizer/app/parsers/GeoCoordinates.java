package com.ugcs.gprvisualizer.app.parsers;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.ugcs.gprvisualizer.utils.Check;

import java.time.LocalDateTime;

public class GeoCoordinates {

    private double latitude;

    private double longitude;

    private Double altitude;

    private LocalDateTime dateTime;

    public GeoCoordinates() {
        this(0,0);
    }

    public GeoCoordinates(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public GeoCoordinates(GeoCoordinates other) {
        this.latitude = other.latitude;
        this.longitude = other.longitude;
        this.altitude = other.altitude;
        this.dateTime = other.dateTime;
    }

    public LatLon getLatLon() {
        return new LatLon(latitude, longitude);
    }

    public void setLatLon(LatLon latLon) {
        Check.notNull(latLon);

        latitude = latLon.getLatDgr();
        longitude = latLon.getLonDgr();
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

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }
}
