package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.chart.tool.projection.math.Polyline;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

import java.util.List;

public class TraceProfile {

    // square root of soil relative permittivity
    private double erSqrt = 1;

    private double sampleIntervalNanos;

    // trace origins (antenna)
    // y = gps + antenna offset
    private List<TraceRay> rays;

    // terrain
    // y = gps - altitude agl = antenna y - antenna offset - altitude agl
    private Polyline terrain;

    private List<Point2D> rawTerrain = List.of();

    private TraceSamples samples;

    public double getErSqrt() {
        return erSqrt;
    }

    public double getRelativePermittivity() {
        return erSqrt * erSqrt;
    }

    public void setRelativePermittivity(double relativePermittivity) {
        erSqrt = Math.sqrt(Math.max(1, relativePermittivity));
    }

    public double getSampleIntervalNanos() {
        return sampleIntervalNanos;
    }

    public void setSampleIntervalNanos(double sampleIntervalNanos) {
        this.sampleIntervalNanos = sampleIntervalNanos;
    }

    public double getSampleTime(int sampleIndex) {
        // one-way travel time
        return 0.5 * sampleIndex * sampleIntervalNanos;
    }

    public TraceRay getRay(int i) {
        return rays.get(i);
    }

    public List<TraceRay> getRays() {
        return rays;
    }

    public void setRays(List<TraceRay> rays) {
        this.rays = rays;
    }

    public Polyline getTerrain() {
        return terrain;
    }

    public void setTerrain(Polyline terrain) {
        this.terrain = terrain;
    }

    public List<Point2D> getRawTerrain() {
        return rawTerrain;
    }

    public void setRawTerrain(List<Point2D> rawTerrain) {
        this.rawTerrain = rawTerrain;
    }

    public TraceSamples getSamples() {
        return samples;
    }

    public void setSamples(TraceSamples samples) {
        this.samples = samples;
    }

    public int numTraces() {
        if (samples == null) {
            return 0;
        }
        return samples.numTraces();
    }

    public int numSamples() {
        if (samples == null) {
            return 0;
        }
        return samples.maxSamples();
    }

    public float getValue(int traceIndex, int sampleIndex) {
        if (samples == null) {
            return Float.NaN;
        }
        return samples.getValue(traceIndex, sampleIndex);
    }

    public Rectangle2D getEnvelope() {
        if (rays == null || rays.isEmpty()) {
            return Rectangle2D.EMPTY;
        }

        Point2D first = rays.getFirst().origin();
        double minX = first.getX();
        double minY = first.getY();
        double maxX = first.getX();
        double maxY = first.getY();

        for (TraceRay ray : rays) {
            Point2D p0 = ray.origin();
            double t = getSampleTime(numSamples() - 1);
            Point2D pn = ray.positionAt(t, erSqrt);

            minX = Math.min(minX, p0.getX());
            minX = Math.min(minX, pn.getX());
            maxX = Math.max(maxX, p0.getX());
            maxX = Math.max(maxX, pn.getX());

            minY = Math.min(minY, p0.getY());
            minY = Math.min(minY, pn.getY());
            maxY = Math.max(maxY, p0.getY());
            maxY = Math.max(maxY, pn.getY());
        }

        return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }
}
