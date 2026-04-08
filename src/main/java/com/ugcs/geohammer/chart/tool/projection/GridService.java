package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.math.Vectors;
import com.ugcs.geohammer.chart.tool.projection.model.Grid;
import com.ugcs.geohammer.chart.tool.projection.model.GridOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.TraceRay;
import com.ugcs.geohammer.util.Check;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GridService {

    private static final double MAX_SECTOR_APERTURE = Math.toRadians(120);

    private final ProjectionModel projectionModel;

    public GridService(ProjectionModel projectionModel) {
        this.projectionModel = projectionModel;
    }

    public Grid buildGrid(TraceProfile traceProfile) {
        if (traceProfile == null) {
            return null;
        }

        boolean cropAir = projectionModel.getGridOptions().isCropAir();
        List<Point2D> boundingPolyline = getBoundingPolyline(traceProfile, cropAir);

        Resolution resolution = Resolution.compute(traceProfile, projectionModel.getGridOptions().getResolution());
        Grid grid = new Grid(boundingPolyline, resolution.cellWidth(), resolution.cellHeight());

        setGridProgress(0);
        try {
            sampleGrid(traceProfile, grid, resolution);
            boolean interpolate = projectionModel.getGridOptions().isInterpolateGrid();
            if (interpolate) {
                interpolateGrid(grid);
            }
            grid.updateMaxDepth();
        } finally {
            setGridProgress(1);
        }
        return grid;
    }

    public List<Point2D> getBoundingPolyline(TraceProfile traceProfile, boolean cropAir) {
        Check.notNull(traceProfile);

        int numTraces = traceProfile.numTraces();
        int numSamples = traceProfile.numSamples();

        List<Point2D> polyline = new ArrayList<>(2 * numTraces);
        // top
        for (TraceRay ray : traceProfile.getRays()) {
            if (cropAir) {
                if (ray.soilOrigin() != null) {
                    polyline.add(ray.soilOrigin());
                }
            } else {
                polyline.add(ray.origin());
            }
        }
        // bottom
        double lastX = Double.NaN;
        for (int i = numTraces - 1; i >= 0; i--) {
            TraceRay ray = traceProfile.getRay(i);
            if (ray.soilOrigin() == null) {
                continue;
            }
            double tn = traceProfile.getSampleTime(numSamples - 1);
            Point2D pn = ray.positionAt(tn, traceProfile.getErSqrt());
            if (Double.isNaN(lastX) || pn.getX() < lastX) {
                polyline.add(pn);
                lastX = pn.getX();
            }
        }
        return polyline;
    }

    private void interpolateGrid(Grid grid) {
        Check.notNull(grid);

        grid.interpolate();
    }

    private void setGridProgress(double progress) {
        GridOptions gridOptions = projectionModel.getGridOptions();
        Platform.runLater(() -> gridOptions.gridProgressProperty().set(progress));
    }

    private List<TraceRay> getSectorRays(TraceProfile traceProfile, TraceRay axis, Resolution resolution) {
        // sector aperture
        double centerFrequency = projectionModel.getProjectionOptions().getCenterFrequency();
        double aperture = getFresnelAperture(axis.airGap(), centerFrequency);
        double fresnelApertureFactor = projectionModel.getGridOptions().getFresnelApertureFactor();
        aperture = Math.min(fresnelApertureFactor * aperture, MAX_SECTOR_APERTURE);

        boolean migration = projectionModel.getGridOptions().isMigration();
        int numRays = migration
                ? resolution.numSectorRays(aperture)
                : 1;
        if (numRays == 1) {
            return List.of(axis);
        }

        // angular step between rays
        double axisAngle = Vectors.angleFromDown(axis.direction());
        double base = axisAngle - 0.5 * aperture;
        double step = aperture / (numRays - 1);

        boolean refract = projectionModel.getGridOptions().isRefraction();

        List<TraceRay> rays = new ArrayList<>(numRays);
        for (int i = 0; i < numRays; i++) {
            double angle = base + i * step;
            Point2D direction = Vectors.directionFromDown(angle);
            TraceRay ray = TraceRay.create(
                    axis.origin(),
                    direction,
                    traceProfile.getTerrain(),
                    traceProfile.getErSqrt(),
                    refract);
            // skip ray if it does not hit terrain
            if (ray.soilOrigin() == null) {
                continue;
            }
            rays.add(ray);
        }
        return rays;
    }

    private static double getFresnelAperture(double airGap, double centerFrequency) {
        // centerFrequency in MHz
        double airWavelength = 1e3 * TraceRay.C_M_NS / centerFrequency;
        double airBuffer = 0.25;
        airGap = !Double.isNaN(airGap)
                ? Math.max(airGap, airBuffer)
                : airBuffer;
        double fresnelRadius = airWavelength / 4 + Math.sqrt(airGap * airWavelength) / 2;
        return 2 * Math.atan(fresnelRadius / airGap);
    }

    private void sampleGrid(TraceProfile traceProfile, Grid grid, Resolution resolution) {
        int numTraces = traceProfile.numTraces();
        int numSamples = traceProfile.numSamples();
        if (numTraces == 0 || numSamples == 0) {
            return;
        }

        int m = grid.getWidth();
        int n = grid.getHeight();

        Point2D gridUnit = grid.getUnit();
        double arcLength = Math.min(gridUnit.getX(), gridUnit.getY());
        double erSqrt = traceProfile.getErSqrt();

        // stores generation index of the last contribution
        // to the cell value
        int[][] seen = new int[m][n];
        int generation = 0;

        int traceStep = resolution.traceStep();
        for (int i = 0; i < numTraces; i += traceStep) {
            setGridProgress((double)i / numTraces);
            generation++;

            TraceRay axis = traceProfile.getRay(i);
            if (axis.soilOrigin() == null) {
                continue; // no terrain hit
            }

            // step angle for the deepest sample
            List<TraceRay> rays = getSectorRays(traceProfile, axis, resolution);
            // cached ray angles
            List<Double> rayAngles = new ArrayList<>(rays.size());
            for (TraceRay ray : rays) {
                rayAngles.add(Vectors.angleFromDown(ray.direction()));
            }

            for (int j = 0; j < numSamples; j++) {
                float value = traceProfile.getValue(i, j);
                if (Float.isNaN(value)) {
                    continue;
                }

                double t = traceProfile.getSampleTime(j);
                Point2D axisSample = axis.positionAt(t, traceProfile.getErSqrt());
                double axisSampleRadius = axisSample.subtract(axis.origin()).magnitude();
                double stepAngle = Resolution.getSectorStep(axisSampleRadius, arcLength);

                double lastAngle = Double.NaN;
                for (int k = 0; k < rays.size(); k++) {
                    TraceRay ray = rays.get(k);
                    // no terrain hit
                    if (ray.soilOrigin() == null) {
                        continue;
                    }
                    // skip by tracing step
                    double rayAngle = rayAngles.get(k);
                    if (!Double.isNaN(lastAngle) && (rayAngle - lastAngle) < stepAngle) {
                        continue;
                    }
                    lastAngle = rayAngle;

                    Point2D sample = ray.positionAt(t, erSqrt);
                    Grid.Index cellIndex = grid.getIndex(sample);
                    if (cellIndex == null) {
                        continue;
                    }
                    Grid.Cell cell = grid.getCell(cellIndex);
                    if (cell == null) {
                        continue;
                    }
                    if (seen[cellIndex.i()][cellIndex.j()] == generation) {
                        continue;
                    }
                    seen[cellIndex.i()][cellIndex.j()] = generation;

                    float depth = (float)sample.subtract(ray.soilOrigin()).magnitude();
                    cell.accumulate(value, depth, 1f);
                }
            }
        }

        grid.normalize();
    }

    record Resolution(
            double cellWidth,
            double cellHeight,
            int traceStep,
            // num summation rays per degree
            double sectorDensity
    ) {

        static final double DEFAULT_CELL_WIDTH = 0.05;

        static final double DEFAULT_CELL_HEIGHT = 0.05;

        static final int MAX_CELL_SCALE = 10;

        static final int MAX_TRACE_STEP = 20;

        static final double MIN_SECTOR_DENSITY = 20 / (Math.PI / 2);

        static final double MAX_SECTOR_DENSITY = 200 / (Math.PI / 2);

        int numSectorRays(double aperture) {
            int numRays = (int)Math.round(aperture * sectorDensity);
            return Math.max(1, numRays);
        }

        static Resolution compute(TraceProfile traceProfile, double resolution) {
            resolution = Math.clamp(resolution, 0, 1);

            double cellWidth = getCellWidth(traceProfile);
            double cellHeight = getCellHeight(traceProfile);

            double cellScale = (1 - resolution) * MAX_CELL_SCALE;
            cellScale = Math.max(1, cellScale);

            int traceStep = (int)Math.round((1 - resolution) * MAX_TRACE_STEP);
            traceStep = Math.max(1, traceStep);

            double arcLength = Math.min(cellWidth, cellHeight);
            double sectorRadius = traceProfile.numSamples() * cellHeight;
            double sectorStep = getSectorStep(sectorRadius, arcLength);
            double sectorDensity = resolution * Math.min(1 / sectorStep, MAX_SECTOR_DENSITY);
            sectorDensity = Math.max(MIN_SECTOR_DENSITY, sectorDensity);

            return new Resolution(
                    cellScale * cellWidth,
                    cellScale * cellHeight,
                    traceStep,
                    sectorDensity);
        }

        static double getCellWidth(TraceProfile traceProfile) {
            int numTraces = traceProfile.numTraces();
            if (numTraces < 2) {
                return DEFAULT_CELL_WIDTH;
            }

            // avg trace spacing along x
            Point2D p0 = traceProfile.getRay(0).origin();
            Point2D pn = traceProfile.getRay(numTraces - 1).origin();
            return Math.abs(pn.getX() - p0.getX()) / (numTraces - 1);
        }

        static double getCellHeight(TraceProfile traceProfile) {
            // length of a sample in a soil
            double t = 0.5 * traceProfile.getSampleIntervalNanos();
            return TraceRay.C_M_NS / traceProfile.getErSqrt() * t;
        }

        static double getSectorStep(double r, double arcLength) {
            // s = r * alpha,
            // s - arc length
            // r - radius
            if (r <= Vectors.EPS) {
                return Math.toRadians(10);
            }
            return arcLength / r;
        }
    }
}
