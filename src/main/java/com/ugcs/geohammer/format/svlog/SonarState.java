package com.ugcs.geohammer.format.svlog;

import com.ugcs.geohammer.model.LatLon;

import java.time.Instant;

public class SonarState {

    private LatLon latLon;

    private Double altitude;

    private Instant timestamp;

    private Double depth;

    private Float vehicleHeading;

    private Float transducerHeading;

    private Double heading;

    private Float pitch;

    private Float roll;

    private Float temperature;

    private Float pressure;

    public LatLon getLatLon() {
        return latLon;
    }

    public void setLatLon(LatLon latLon) {
        this.latLon = latLon;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Double getDepth() {
        return depth;
    }

    public void setDepth(Double depth) {
        this.depth = depth;
    }

    public Float getVehicleHeading() {
        return vehicleHeading;
    }

    public void setVehicleHeading(Float vehicleHeading) {
        this.vehicleHeading = vehicleHeading;
    }

    public Float getTransducerHeading() {
        return transducerHeading;
    }

    public void setTransducerHeading(Float transducerHeading) {
        this.transducerHeading = transducerHeading;
    }

    public Double getHeading() {
        return heading;
    }

    public void setHeading(Double heading) {
        this.heading = heading;
    }

    public Float getPitch() {
        return pitch;
    }

    public void setPitch(Float pitch) {
        this.pitch = pitch;
    }

    public Float getRoll() {
        return roll;
    }

    public void setRoll(Float roll) {
        this.roll = roll;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Float getPressure() {
        return pressure;
    }

    public void setPressure(Float pressure) {
        this.pressure = pressure;
    }
}
