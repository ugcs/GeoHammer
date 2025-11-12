package com.ugcs.geohammer.format;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class GeoCoordinates {

    private double latitude;

    private double longitude;

    // -1 for empty value
    private long timestamp = -1;

    public GeoCoordinates() {
    }

    public GeoCoordinates(GeoCoordinates other) {
        Check.notNull(other);

        this.latitude = other.latitude;
        this.longitude = other.longitude;
        this.timestamp = other.timestamp;
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

    public LocalDateTime getDateTime() {
        return timestamp != -1
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
                : null;
    }

    public void setDateTime(LocalDateTime dateTime) {
        timestamp = dateTime != null
                ? dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
                : -1;
    }
}
