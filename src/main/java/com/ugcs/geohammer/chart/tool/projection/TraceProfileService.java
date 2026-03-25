package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.math.GaussianFilter;
import com.ugcs.geohammer.chart.tool.projection.math.LinearInterpolator;
import com.ugcs.geohammer.chart.tool.projection.math.Normal;
import com.ugcs.geohammer.chart.tool.projection.math.NormalDiffusion;
import com.ugcs.geohammer.chart.tool.projection.math.Polyline;
import com.ugcs.geohammer.chart.tool.projection.math.ResamplingFilter;
import com.ugcs.geohammer.chart.tool.projection.math.TraceFilter;
import com.ugcs.geohammer.chart.tool.projection.model.BackgroundFilter;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSamples;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSamplesView;
import com.ugcs.geohammer.format.HorizontalProfile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import javafx.geometry.Point2D;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TraceProfileService {

    private final ProjectionModel projectionModel;

    public TraceProfileService(ProjectionModel projectionModel) {
        this.projectionModel = projectionModel;
    }

    public TraceProfile buildTraceProfile() {
        TraceFile file = projectionModel.getSelection().getFile();
        if (file == null) {
            return null;
        }

        Integer line = projectionModel.getSelection().getLine();
        if (line == null) {
            line = 0;
        }
        IndexRange range = file.getLineRanges().get(line);
        if (range == null) {
            range = new IndexRange(0, file.numTraces());
        }

        TraceProfile traceProfile = new TraceProfile();

        double relativePermittivity = projectionModel
                .getProjectionOptions()
                .getRelativePermittivity();
        traceProfile.setRelativePermittivity(relativePermittivity);

        // sgy sample interval is in microseconds
        double sampleIntervalNanos = 1e-3 * file.getSampleInterval();
        traceProfile.setSampleIntervalNanos(sampleIntervalNanos);

        List<Point2D> origins = buildOrigins(file, range);
        traceProfile.setOrigins(origins);

        List<Point2D> terrain = buildTerrain(file, range, origins);
        traceProfile.setTerrain(terrain);

        List<Point2D> terrainFiltered = filterTerrain(terrain, origins);
        traceProfile.setTerrainFiltered(terrainFiltered);

        List<Normal> normals = buildNormals(origins, terrainFiltered);
        traceProfile.setNormals(normals);

        int sampleOffset = projectionModel
                .getProjectionOptions()
                .getSampleOffset();
        TraceSamples samples = new TraceSamplesView(file, range, sampleOffset);
        boolean removeBackground = projectionModel
                .getRenderOptions()
                .isRemoveBackground();
        if (removeBackground) {
            samples = new BackgroundFilter(samples);
        }
        traceProfile.setSamples(samples);

        return traceProfile;
    }

    private List<Point2D> buildOrigins(TraceFile file, IndexRange range) {
        double antennaOffset = projectionModel
                .getProjectionOptions()
                .getAntennaOffset();

        double[] x = getX(file, range);
        double[] y = getAntennaY(file, range, antennaOffset);

        int n = x.length;
        Check.condition(y.length == n);

        List<Point2D> origins = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            origins.add(new Point2D(x[i], y[i]));
        }

        // filter
        double smoothingRadius = projectionModel
                .getProjectionOptions()
                .getAntennaSmoothingRadius();
        if (smoothingRadius > 0) {
            TraceFilter filter = new GaussianFilter(smoothingRadius);
            origins = filter.filter(origins);
        }

        return origins;
    }

    private double[] getX(TraceFile file, IndexRange range) {
        List<Trace> traces = file.getTraces();
        int n = range.size();

        double[] x = new double[n];
        for (int i = 1; i < n; i++) {
            int traceIndex = range.from() + i;
            LatLon latLon0 = traces.get(traceIndex - 1).getLatLon();
            LatLon latLon1 = traces.get(traceIndex).getLatLon();
            Point2D p0 = SphericalMercator.project(latLon0);
            Point2D p1 = SphericalMercator.project(latLon1);
            double d = p1.subtract(p0).magnitude();
            // Lproj / L = k
            double k = SphericalMercator.scaleFactorAt(latLon0.getLatDgr());
            d /= k;
            x[i] = x[i - 1] + d;
        }
        return x;
    }

    private double[] getAntennaY(TraceFile file, IndexRange range, double antennaOffset) {
        List<Trace> traces = file.getTraces();
        int n = range.size();

        HorizontalProfile profile = file.getGroundProfile();
        double[] gpsY = profile != null
                ? profile.getEllipsoidalHeights()
                : null;
        double[] antennaY = new double[n];
        for (int i = 0; i < n; i++) {
            int traceIndex = range.from() + i;
            int fileTraceIndex = file.getFileTraceIndex(traceIndex);
            antennaY[i] = gpsY != null && fileTraceIndex < gpsY.length
                    ? gpsY[fileTraceIndex] + antennaOffset
                    : traces.get(traceIndex).getReceiverAltitude() + antennaOffset;
        }
        return antennaY;
    }

    private List<Point2D> buildTerrain(TraceFile file, IndexRange range, List<Point2D> origins) {
        int n = range.size();
        Check.condition(origins.size() == n);

        double antennaOffset = projectionModel
                .getProjectionOptions()
                .getAntennaOffset();
        double terrainOffset = projectionModel
                .getProjectionOptions()
                .getTerrainOffset();

        HorizontalProfile profile = file.getGroundProfile();
        double[] agl = profile != null
                ? profile.getAltitudes()
                : null;

        List<Point2D> terrain = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int traceIndex = range.from() + i;
            int fileTraceIndex = file.getFileTraceIndex(traceIndex);

            Point2D origin = origins.get(i);
            double altitude = agl != null && fileTraceIndex < agl.length
                    ? agl[fileTraceIndex]
                    : 0;
            // antennaY = gpsY + antennaOffset
            // terrain = gpsY - altitude
            terrain.add(new Point2D(
                    origin.getX() + terrainOffset,
                    origin.getY() - Math.max(antennaOffset + altitude, 0)
            ));
        }
        return terrain;
    }

    private List<Point2D> filterTerrain(List<Point2D> terrain, List<Point2D> origins) {
        // resample terrain
        double minResamplingStep = 0.005;

        LinearInterpolator interpolator = new LinearInterpolator();
        ResamplingFilter resampling = new ResamplingFilter(interpolator, minResamplingStep);

        List<Point2D> filtered = resampling.filter(terrain);

        double smoothingRadius = projectionModel
                .getProjectionOptions()
                .getTerrainSmoothingRadius();
        if (smoothingRadius > 0) {
            TraceFilter smoothFilter = new GaussianFilter(smoothingRadius);
            filtered = smoothFilter.filter(filtered);
        }

        return filtered;
    }

    private List<Normal> buildNormals(List<Point2D> origins, List<Point2D> terrain) {
        if (Nulls.toEmpty(terrain).size() < 2) {
            return List.of();
        }

        Polyline polyline = new Polyline(terrain);

        boolean diffuseNormals = projectionModel
                .getProjectionOptions()
                .isDiffuseNormals();
        if (diffuseNormals) {
            NormalDiffusion normalDiffusion = new NormalDiffusion();
            return normalDiffusion.getNormals(origins, polyline);
        } else {
            int n = origins.size();
            List<Normal> normals = new ArrayList<>(n);
            for (Point2D p : origins) {
                Polyline.Projection projected = polyline.project(p);
                Point2D v = projected.point().subtract(p);
                Normal normal = new Normal(v.normalize(), v.magnitude());
                normals.add(normal);
            }
            return normals;
        }
    }
}
