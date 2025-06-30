package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import org.apache.commons.lang3.NotImplementedException;

public abstract class ScrollableData {

    protected int startSample = 0;

    private double realAspect = 0.5;
    private int middleTrace;
    private double vertScale = 1.0;

    private int zoom = 1;
    public static final double ZOOM_A = 1.05;

    private final ProfileScroll profileScroll;

    public ScrollableData(Model model) {
        this.profileScroll = new ProfileScroll(model, this);
    }

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        this.zoom = Math.clamp(zoom, 1, 100);
        vertScale = Math.pow(ZOOM_A, zoom);
    }

    public final int getMiddleTrace() {
        return middleTrace;
    }

    public void setMiddleTrace(int selectedTrace) {
        this.middleTrace = selectedTrace;
    }

    public abstract int getVisibleNumberOfTrace();

    public abstract int getTracesCount();

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
        setZoom(1);
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

    public Point2D traceSampleToScreen(TraceSample traceSample) {
        // for GPRChart
        throw new NotImplementedException("traceSampleToScreen");
    }
}