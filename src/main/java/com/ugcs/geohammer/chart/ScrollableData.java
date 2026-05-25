package com.ugcs.geohammer.chart;

import com.ugcs.geohammer.model.TraceSample;
import com.ugcs.geohammer.model.Model;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;

public abstract class ScrollableData {

    public static final double ZOOM_IN = 1.05;

    public static final double ZOOM_OUT = 1.0 / ZOOM_IN;

    private final ProfileScroll profileScroll;

    // pixels per trace
    private double horizontalScale = 1;

    // pixels per sample
    private double verticalScale = 1;

    private int startTrace = 0;

    private int startSample = 0;

    public ScrollableData(Model model) {
        this.profileScroll = new ProfileScroll(this);
    }

    public ProfileScroll getProfileScroll() {
        return profileScroll;
    }

    public double getHorizontalScale() {
        return horizontalScale;
    }

    public void setHorizontalScale(double horizontalScale) {
        // limit min horizontal scale
        int numTraces = numTraces();
        double minHorizontalScale = numTraces != 0
                ? getViewWidth() / numTraces
                : 1.0;
        this.horizontalScale = Math.max(horizontalScale, minHorizontalScale);
    }

    public double getVerticalScale() {
        return verticalScale;
    }

    public void setVerticalScale(double verticalScale) {
        // limit min vertical scale
        int numSamples = numSamples();
        double minVerticalScale = numSamples != 0
                ? getViewHeight() / numSamples
                : 1.0;
        this.verticalScale = Math.max(verticalScale, minVerticalScale);
    }

    public int getStartTrace() {
        return startTrace;
    }

    public void setStartTrace(int startTrace) {
        double numVisibleTraces = getViewWidth() / horizontalScale;
        startTrace = Math.min(startTrace, numTraces() - (int)numVisibleTraces);
        startTrace = Math.max(startTrace, 0);
        this.startTrace = startTrace;
    }

    public int getMiddleTrace() {
        double numVisibleTraces = getViewWidth() / horizontalScale;
        return startTrace + (int)(0.5 * numVisibleTraces);
    }

    public void scrollToTrace(int trace) {
        double numVisibleTraces = getViewWidth() / horizontalScale;
        setStartTrace(trace - (int)(0.5 * numVisibleTraces));
    }

    public int getStartSample() {
        return startSample;
    }

    public void setStartSample(int startSample) {
        double numVisibleSamples = getViewHeight() / verticalScale;
        startSample = Math.min(startSample, numSamples() - (int)numVisibleSamples);
        startSample = Math.max(startSample, 0);
        this.startSample = startSample;
    }

    public abstract double getViewWidth();

    public abstract double getViewHeight();

    public abstract int numTraces();

    public abstract int numSamples();

    public int traceToScreen(int trace) {
        double x = (trace - startTrace) * horizontalScale;
        return (int)(x - 0.5 * getViewWidth());
    }

    public TraceSample screenToTraceSample(Point2D point) {
        throw new UnsupportedOperationException();
    }

    public Point2D traceSampleToScreen(int trace, int sample) {
        throw new UnsupportedOperationException();
    }

    public Point2D traceSampleToScreenCenter(int trace, int sample) {
        throw new UnsupportedOperationException();
    }

    public void setCursor(Cursor cursor) {
        throw new UnsupportedOperationException();
    }
}