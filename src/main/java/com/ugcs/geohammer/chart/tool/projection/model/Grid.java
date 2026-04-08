package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import java.util.List;

public class Grid {

    private static final GeometryFactory gf = new GeometryFactory();

    private static final int MAX_CELLS = 10_000_000;

    private final Point2D origin;

    private final Point2D unit;

    private final PreparedGeometry bounds;

    // x, y
    private final Cell[][] cells;

    private float maxDepth;

    public Grid(List<Point2D> boundingPolyline, double cellWidth, double cellHeight) {
        Check.notEmpty(boundingPolyline);
        Check.condition(cellWidth > 0);
        Check.condition(cellHeight > 0);

        bounds = asSimplifiedPolygon(boundingPolyline);
        Envelope envelope = bounds.getGeometry().getEnvelopeInternal();

        // (minX, minY) center of the (0, 0) grid cell
        // (maxX, maxY) center of the (m - 1, n - 1) grid cell,
        // cell size is gridUnit
        int m = (int) Math.ceil(envelope.getWidth() / cellWidth) + 1;
        int n = (int) Math.ceil(envelope.getHeight() / cellHeight) + 1;

        double k = (double)MAX_CELLS / ((long)m * n);
        if (k < 1.0) {
            double kSqrt = Math.sqrt(k);
            m = (int)(kSqrt * m);
            n = (int)(kSqrt * n);
            cellWidth = envelope.getWidth() / (m - 1);
            cellHeight = envelope.getHeight() / (n - 1);
        }

        origin = new Point2D(envelope.getMinX(), envelope.getMinY());
        unit = new Point2D(cellWidth, cellHeight);

        // init cells
        cells = new Cell[m][n];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                Coordinate coordinate = getCoordinate(i, j);
                if (bounds.contains(gf.createPoint(coordinate))) {
                    cells[i][j] = new Cell();
                }
            }
        }
    }

    private static PreparedGeometry asSimplifiedPolygon(List<Point2D> polyline) {
        Check.notNull(polyline);

        int n = polyline.size();
        Coordinate[] coordinates = new Coordinate[n + 1];
        for (int i = 0; i < n; i++) {
            Point2D point = polyline.get(i);
            coordinates[i] = new Coordinate(point.getX(), point.getY());
        }
        coordinates[n] = coordinates[0];

        LinearRing shell = gf.createLinearRing(coordinates);
        Polygon polygon = gf.createPolygon(shell);
        // buffer(0) fixes self-intersections but may produce MultiPolygon
        Geometry geometry = polygon.buffer(0);
        geometry = DouglasPeuckerSimplifier.simplify(geometry, 1e-2);
        geometry = getLargestGeometry(geometry);
        return PreparedGeometryFactory.prepare(geometry);
    }

    private static Geometry getLargestGeometry(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        int n = geometry.getNumGeometries();
        if (n <= 1) {
            return geometry;
        }
        Geometry largest = geometry.getGeometryN(0);
        for (int i = 1; i < n; i++) {
            Geometry part = geometry.getGeometryN(i);
            if (part.getArea() > largest.getArea()) {
                largest = part;
            }
        }
        return largest;
    }

    public Point2D getOrigin() {
        return origin;
    }

    public Point2D getUnit() {
        return unit;
    }

    public int getWidth() {
        return cells.length;
    }

    public int getHeight() {
        return cells.length > 0 ? cells[0].length : 0;
    }

    public Grid.Index getIndex(Point2D point) {
        int i = (int) Math.round((point.getX() - origin.getX()) / unit.getX());
        int j = (int) Math.round((point.getY() - origin.getY()) / unit.getY());
        if (i < 0 || i >= cells.length || j < 0 || j >= cells[0].length) {
            return null;
        }
        return new Index(i, j);
    }

    public Point2D getPoint(Index index) {
        if (index == null) {
            return null;
        }
        return new Point2D(
                origin.getX() + index.i * unit.getX(),
                origin.getY() + index.j * unit.getY()
        );
    }

    private Coordinate getCoordinate(int i, int j) {
        return new Coordinate(
                origin.getX() + i * unit.getX(),
                origin.getY() + j * unit.getY()
        );
    }

    boolean contains(Index index) {
        return index != null
                && index.i >= 0 && index.i < getWidth()
                && index.j >= 0 && index.j < getHeight();
    }

    public Cell getCell(Index index) {
        if (index == null) {
            return null;
        }
        return cells[index.i][index.j];
    }

    public void setCell(Index index, Cell cell) {
        if (!contains(index)) {
            return;
        }
        Check.condition(Float.isNaN(cell.value) || !Float.isNaN(cell.depth));
        cells[index.i][index.j] = cell;
    }

    public float getMaxDepth() {
        return maxDepth;
    }

    public void updateMaxDepth() {
        int m = getWidth();
        int n = getHeight();
        float maxDepth = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                Cell cell = cells[i][j];
                if (cell != null && !Float.isNaN(cell.depth)) {
                    maxDepth = Math.max(maxDepth, cell.depth);
                }
            }
        }
        this.maxDepth = maxDepth;
    }

    public boolean inBounds(int i, int j) {
        return i >= 0 && i < cells.length && j >= 0 && j < cells[i].length && cells[i][j] != null;
    }

    public void normalize() {
        int m = getWidth();
        int n = getHeight();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                Cell cell = cells[i][j];
                if (cell != null) {
                    cell.normalize();
                }
            }
        }
    }

    public void interpolate() {
        int m = getWidth();
        int n = getHeight();

        // horizontal interpolation (along track for each depth level)
        // this fills gaps between diverging trace projections
        for (int j = 0; j < n; j++) {
            int left = -1;
            for (int i = 0; i < m; i++) {
                Cell cell = cells[i][j];
                if (cell != null && !Float.isNaN(cell.value)) {
                    if (left >= 0 && i - left > 1) {
                        Cell leftCell = cells[left][j];
                        Cell rightCell = cells[i][j];
                        int gap = i - left;
                        for (int k = left + 1; k < i; k++) {
                            if (inBounds(k, j)) {
                                float t = (float) (k - left) / gap;
                                cells[k][j] = Cell.interpolate(leftCell, rightCell, t);
                            }
                        }
                    }
                    left = i;
                }
            }
        }

        // vertical interpolation (along depth for each column)
        // fills remaining gaps in the depth direction
        for (int i = 0; i < m; i++) {
            int top = -1;
            for (int j = 0; j < n; j++) {
                Cell cell = cells[i][j];
                if (cell != null && !Float.isNaN(cell.value)) {
                    if (top >= 0 && j - top > 1) {
                        Cell topCell = cells[i][top];
                        Cell bottomCell = cells[i][j];
                        int gap = j - top;
                        for (int k = top + 1; k < j; k++) {
                            if (inBounds(i, k)) {
                                float t = (float) (k - top) / gap;
                                cells[i][k] = Cell.interpolate(topCell, bottomCell, t);
                            }
                        }
                    }
                    top = j;
                }
            }
        }
    }

    public record Index(int i, int j) {
    }

    public static class Cell {

        private float value = Float.NaN;

        private float depth = Float.NaN;

        private float weight = 1f;

        public float getValue() {
            return value;
        }

        public void setValue(float value) {
            this.value = value;
        }

        public float getDepth() {
            return depth;
        }

        public void setDepth(float depth) {
            this.depth = depth;
        }

        public float getWeight() {
            return weight;
        }

        public void setWeight(float weight) {
            this.weight = weight;
        }

        public void accumulate(float value, float depth, float weight) {
            if (Float.isNaN(value)) {
                return; // skip
            }
            Check.condition(!Float.isNaN(depth));
            if (Float.isNaN(this.value)) {
                this.value = value * weight;
                this.depth = depth * weight;
                this.weight = weight;
            } else {
                this.value += value * weight;
                this.depth += depth * weight;
                this.weight += weight;
            }
        }

        public void normalize() {
            if (weight == 1f) {
                return;
            }
            if (Float.isNaN(value) || weight == 0f) {
                value = Float.NaN;
                depth = Float.NaN;
                weight = 1f;
            } else {
                value /= weight;
                depth /= weight;
                weight = 1f;
            }
        }

        public static Cell interpolate(Cell a, Cell b, float t) {
            Cell interpolated = new Cell();
            interpolated.value = a.value + t * (b.value - a.value);
            interpolated.depth = a.depth + t * (b.depth - a.depth);
            interpolated.weight = 1f;
            return interpolated;
        }
    }
}
