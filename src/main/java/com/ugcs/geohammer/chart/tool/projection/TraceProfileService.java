package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.math.GaussianFilter;
import com.ugcs.geohammer.chart.tool.projection.math.Polyline;
import com.ugcs.geohammer.chart.tool.projection.math.ResampleFilter;
import com.ugcs.geohammer.chart.tool.projection.math.TraceFilter;
import com.ugcs.geohammer.chart.tool.projection.math.Vectors;
import com.ugcs.geohammer.chart.tool.projection.model.BackgroundFilter;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionOptions;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.TraceRay;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSamples;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSamplesView;
import com.ugcs.geohammer.format.HorizontalProfile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TraceProfileService {

    private static final int RANGE_LIMIT = 5000;

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
        if (range.size() > RANGE_LIMIT) {
            range = new IndexRange(range.from(), range.from() + RANGE_LIMIT);
        }

        TraceProfile traceProfile = new TraceProfile();

        double relativePermittivity = projectionModel.getProjectionOptions().getRelativePermittivity();
        traceProfile.setRelativePermittivity(relativePermittivity);

        // sgy sample interval is in microseconds
        double sampleIntervalNanos = 1e-3 * file.getSampleInterval();
        traceProfile.setSampleIntervalNanos(sampleIntervalNanos);

        List<Point2D> origins = buildOrigins(file, range);
        List<Point2D> rawTerrain = buildTerrain(file, range, origins);
        List<Point2D> filteredTerrain = filterTerrain(rawTerrain);
        Polyline terrain = new Polyline(filteredTerrain);

        traceProfile.setTerrain(terrain);
        traceProfile.setRawTerrain(rawTerrain);

        List<TraceRay> rays = buildTraceRays(origins, terrain, Math.sqrt(relativePermittivity));
        traceProfile.setRays(rays);

        int sampleOffset = projectionModel.getProjectionOptions().getSampleOffset();
        boolean removeBackground = projectionModel.getRenderOptions().isRemoveBackground();

        TraceSamples samples = new TraceSamplesView(file, range, sampleOffset);
        if (removeBackground) {
            samples = new BackgroundFilter(samples);
        }
        traceProfile.setSamples(samples);

        return traceProfile;
    }

    private List<Point2D> buildOrigins(TraceFile file, IndexRange range) {
        double[] x = getX(file, range);
        double[] y = getAntennaY(file, range);

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

    private double[] getAntennaY(TraceFile file, IndexRange range) {
        List<Trace> traces = file.getTraces();
        int n = range.size();

        HorizontalProfile profile = file.getGroundProfile();
        double[] gpsY = profile != null
                ? profile.getEllipsoidalHeights()
                : null;
        double[] antennaY = new double[n];

        double antennaOffset = projectionModel
                .getProjectionOptions()
                .getAntennaOffset();

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

    private List<Point2D> filterTerrain(List<Point2D> terrain) {
        ResampleFilter resampleFilter = new ResampleFilter(ProjectionOptions.MIN_RESAMPLING_STEP);
        List<Point2D> filtered = resampleFilter.filter(terrain);

        double smoothingRadius = projectionModel
                .getProjectionOptions()
                .getTerrainSmoothingRadius();
        if (smoothingRadius > 0) {
            TraceFilter smoothFilter = new GaussianFilter(smoothingRadius);
            filtered = smoothFilter.filter(filtered);
        }

        return filtered;
    }

    private List<TraceRay> buildTraceRays(List<Point2D> origins, Polyline terrain, double erSqrt) {

        Point2D yDown = new Point2D(0, -1);
        double normalWeight = projectionModel.getProjectionOptions().getNormalWeight();
        boolean refract = projectionModel.getGridOptions().isRefraction();

        int n = origins.size();
        List<TraceRay> rays = new ArrayList<>(n);
        for (Point2D origin : origins) {
            Polyline.SegmentPoint projected = terrain.project(origin);
            Point2D normal = projected != null
                    ? projected.point().subtract(origin).normalize()
                    : yDown;

            Point2D direction = Vectors.weightedBisector(yDown, normal, normalWeight);
            TraceRay ray = TraceRay.create(origin, direction, terrain, erSqrt, refract);
            rays.add(ray);
        }
        return rays;
    }


}
