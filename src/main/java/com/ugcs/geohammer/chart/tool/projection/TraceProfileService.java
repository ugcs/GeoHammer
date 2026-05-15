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
import com.ugcs.geohammer.chart.tool.projection.model.TraceSelection;
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
        TraceSelection selection = projectionModel.getSelection();
        TraceFile file = selection.getFile();
        if (file == null) {
            return null;
        }
        Integer line = selection.getLine();
        if (line == null) {
            line = 0;
        }
        return buildTraceProfile(file, line);
    }

    public TraceProfile buildTraceProfile(TraceFile file, int line) {
        if (file == null) {
            return null;
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

        List<Point2D> projection = projectOrigins(file, range);
        double[] x = offsetsAlongLine(projection);
        double[] y = getAntennaY(file, range);
        List<Point2D> origins = buildOrigins(x, y);
        List<Point2D> rawTerrain = buildTerrain(file, range, origins);
        List<Point2D> filteredTerrain = filterTerrain(rawTerrain);
        Polyline terrain = new Polyline(filteredTerrain);

        traceProfile.setTerrain(terrain);
        traceProfile.setRawTerrain(rawTerrain);

        List<TraceRay> rays = buildTraceRays(projection, origins, terrain, Math.sqrt(relativePermittivity));
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

    private List<Point2D> buildOrigins(double[] x, double[] y) {
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

    private List<Point2D> projectOrigins(TraceFile file, IndexRange range) {
        List<Trace> traces = file.getTraces();
        int n = range.size();

        List<Point2D> projected = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int traceIndex = range.from() + i;
            LatLon latLon = traces.get(traceIndex).getLatLon();
            projected.add(SphericalMercator.project(latLon));
        }
        return projected;
    }

    private double[] offsetsAlongLine(List<Point2D> points) {
        int n = points.size();
        if (n == 0) {
            return new double[0];
        }
        // Lproj / L = k
        LatLon first = SphericalMercator.restore(points.getFirst());
        double k = SphericalMercator.scaleFactorAt(first.getLatDgr());
        double[] offsets = new double[n];
        for (int i = 1; i < n; i++) {
            double offset = points.get(i).subtract(points.get(i - 1)).magnitude() / k;
            offsets[i] = offsets[i - 1] + offset;
        }
        return offsets;
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

    private List<TraceRay> buildTraceRays(List<Point2D> projection, List<Point2D> origins,
            Polyline terrain, double erSqrt) {
        Check.condition(projection.size() == origins.size());

        Point2D yDown = new Point2D(0, -1);
        double normalWeight = projectionModel.getProjectionOptions().getNormalWeight();
        boolean refract = projectionModel.getGridOptions().isRefraction();

        int n = origins.size();
        List<TraceRay> rays = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Point2D origin = origins.get(i);
            Polyline.SegmentPoint projectedToTerrain = terrain.project(origin);
            Point2D normal = projectedToTerrain != null
                    ? projectedToTerrain.point().subtract(origin).normalize()
                    : yDown;

            Point2D direction = Vectors.weightedBisector(yDown, normal, normalWeight);
            TraceRay ray = TraceRay.create(projection.get(i), origin, direction, terrain, erSqrt, refract);
            rays.add(ray);
        }
        return rays;
    }
}
