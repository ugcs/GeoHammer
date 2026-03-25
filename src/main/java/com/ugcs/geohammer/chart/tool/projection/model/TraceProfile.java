package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.chart.tool.projection.math.Normal;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

import java.util.List;

public class TraceProfile {

    private static final double C_M_NS = 0.299792458; // m/ns

    // square root of soil relative permittivity
    private double erSqrt = 1;

    private double sampleIntervalNanos;

    // trace origins (antenna)
    // y = gps + antenna offset
    private List<Point2D> origins = List.of();

    // terrain
    // y = gps - altitude agl = antenna y - antenna offset - altitude agl
    private List<Point2D> terrain = List.of();

    // terrain filtered
    private List<Point2D> terrainFiltered = List.of();

    private List<Normal> normals = List.of();

    private TraceSamples samples;

    public double getRelativePermittivity() {
        return erSqrt * erSqrt;
    }

    public void setRelativePermittivity(double relativePermittivity) {
        erSqrt = Math.sqrt(relativePermittivity);
    }

    public double getSampleIntervalNanos() {
        return sampleIntervalNanos;
    }

    public void setSampleIntervalNanos(double sampleIntervalNanos) {
        this.sampleIntervalNanos = sampleIntervalNanos;
    }

    public Point2D getOrigin(int i) {
        return origins.get(i);
    }

    public List<Point2D> getOrigins() {
        return origins;
    }

    public void setOrigins(List<Point2D> origins) {
        this.origins = origins;
    }

    public Point2D getTerrain(int i) {
        return terrain.get(i);
    }

    public List<Point2D> getTerrain() {
        return terrain;
    }

    public void setTerrain(List<Point2D> terrain) {
        this.terrain = terrain;
    }

    public Point2D getTerrainFiltered(int i) {
        return terrainFiltered.get(i);
    }

    public List<Point2D> getTerrainFiltered() {
        return terrainFiltered;
    }

    public void setTerrainFiltered(List<Point2D> terrainFiltered) {
        this.terrainFiltered = terrainFiltered;
    }

    public Normal getNormal(int index) {
        return normals.get(index);
    }

    public List<Normal> getNormals() {
        return normals;
    }

    public void setNormals(List<Normal> normals) {
        this.normals = normals;
    }

    public double getSampleDepth(int traceIndex, int sampleIndex) {
        Normal normal = normals.get(traceIndex);
        Check.notNull(normal);
        return getSampleDepth(sampleIndex, normal.length());
    }

    public double getSampleDepth(int sampleIndex, double airGap) {
        // one-way travel time
        double t = 0.5 * sampleIndex * sampleIntervalNanos;
        double tAir = airGap / C_M_NS;
        if (t <= tAir) {
            return C_M_NS * t;
        } else {
            double tSoil = t - tAir;
            return airGap + C_M_NS / erSqrt * tSoil;
        }
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
        double minX = 0;
        double minY = 0;
        double maxX = 0;
        double maxY = 0;

        int n = origins.size();
        for (int i = 0; i < n; i++) {
            Point2D p0 = origins.get(i);
            Normal normal = normals.get(i);
            double depth = getSampleDepth(samples.numSamples(i) - 1, normal.length());
            Point2D pn = p0.add(normal.unit().multiply(depth));

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
