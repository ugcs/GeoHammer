package com.ugcs.geohammer.chart;

import com.ugcs.geohammer.model.TraceSample;
import com.ugcs.geohammer.model.Model;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import org.apache.commons.lang3.NotImplementedException;

public abstract class ScrollableData {

    protected int startSample = 0;

    private double realAspect = 0.5;
    private int middleTrace;
    private double vertScale = 1.0;

    private double zoom = 0.0;
    public static final double ZOOM_A = 1.05;

    private final ProfileScroll profileScroll;

    public ScrollableData(Model model) {
        this.profileScroll = new ProfileScroll(model, this);
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = Math.clamp(zoom, -100.0, 100.0);
        vertScale = Math.pow(ZOOM_A, zoom);
    }

    public final int getMiddleTrace() {
        return middleTrace;
    }

    public void setMiddleTrace(int selectedTrace) {
        this.middleTrace = selectedTrace;
    }

    public abstract int numVisibleTraces();

    public abstract int numTraces();

    public void setRealAspect(double realAspect) {
        this.realAspect = realAspect;
    }

    public double getRealAspect() {
        return realAspect;
    }

    public double getVScale() {
        return vertScale;
    }

    public double getHScale() {
        return vertScale * realAspect;
    }

    public int traceToScreen(int trace) {
        return (int) ((trace - middleTrace) * getHScale());
    }

    public final int getStartSample() {
        return startSample;
    }

    protected void clear() {
        setZoom(0.0);
    }

    public ProfileScroll getProfileScroll() {
        return profileScroll;
    }

    public void setCursor(Cursor aDefault) {
        // for GPRChart
        throw new NotImplementedException("setCursor");
    }

    public TraceSample screenToTraceSample(Point2D point) {
        // for GPRChart
        throw new NotImplementedException("screenToTraceSample");
    }

    public Point2D traceSampleToScreen(int trace, int sample) {
        // for GPRChart
        throw new NotImplementedException("traceSampleToScreen");
    }

    public Point2D traceSampleToScreenCenter(int trace, int sample) {
        throw new NotImplementedException("traceSampleToScreenCenter");
    }
}