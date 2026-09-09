package com.ugcs.geohammer.map.layer.radar;

public class RadarSettings {

    private boolean radarMapVisible = true;

    private boolean autoGain = true;

    private double topGain = 10;

    private double bottomGain = 12.5;

    private double threshold = 0;

    private int radius = 15;

    public boolean isRadarMapVisible() {
        return radarMapVisible;
    }

    public void setRadarMapVisible(boolean radarMapVisible) {
        this.radarMapVisible = radarMapVisible;
    }

    public boolean isAutoGain() {
        return autoGain;
    }

    public void setAutoGain(boolean autoGain) {
        this.autoGain = autoGain;
    }

    public double getTopGain() {
        return topGain;
    }

    public void setTopGain(double topGain) {
        this.topGain = topGain;
    }

    public double getBottomGain() {
        return bottomGain;
    }

    public void setBottomGain(double bottomGain) {
        this.bottomGain = bottomGain;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }
}
