package com.ugcs.geohammer.service.survey;

import com.ugcs.geohammer.math.PrincipalComponents;
import com.ugcs.geohammer.math.QuickSelect;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

// Line spacing of a track from survey lines reconstructed in the frame of the dominant
// track direction, where x runs along the lines and y across them. A line-by-line survey
// goes back and forth along x, so lines are the stretches between reversals of x,
// and the spacing is the distance between their mean y.
final class LineSpacing {

    private static final double MIN_LINE_LENGTH_RATIO = 0.1;

    private LineSpacing() {
    }

    public static OptionalDouble estimate(double[] x, double[] y) {
        Check.notNull(x);
        Check.notNull(y);
        Check.condition(x.length == y.length);

        // a track without points has no lines
        if (x.length == 0) {
            return OptionalDouble.empty();
        }

        // find primary direction of the offsets
        Point2D direction = PrincipalComponents.findOffsetComponents(x, y).primary();

        // rotate track to the dominant direction inplace
        rotate(x, y, direction);

        double minLineLength = MIN_LINE_LENGTH_RATIO * estimateLineLength(x);
        List<StretchStatistics> lines = detectLines(x, y, minLineLength);

        return computeSpacing(lines, minLineLength);
    }

    private static OptionalDouble computeSpacing(List<StretchStatistics> lines, double minOverlap) {
        Check.notNull(lines);
        // a spacing needs at least two lines
        if (lines.size() < 2) {
            return OptionalDouble.empty();
        }
        lines.sort(Comparator.comparingDouble(StretchStatistics::yMean));

        // spacing of a line is half of the distance to the second neighbor above it:
        // the mean of two gaps, which evens out the lines shifted in pairs
        double[] spacings = new double[lines.size() - 1];
        int numSpacings = 0;
        for (int i = 0; i < lines.size() - 1; i++) {
            int next = nextNeighbor(lines, i, minOverlap);
            if (next == -1) {
                continue;
            }
            int nextNext = nextNeighbor(lines, next, minOverlap);
            spacings[numSpacings++] = nextNext != -1
                    ? 0.5 * (lines.get(nextNext).yMean() - lines.get(i).yMean())
                    : lines.get(next).yMean() - lines.get(i).yMean();
        }
        return numSpacings > 0
                ? OptionalDouble.of(QuickSelect.getMedian(spacings, new IndexRange(0, numSpacings)))
                : OptionalDouble.empty();
    }

    private static void rotate(double[] x, double[] y, Point2D direction) {
        direction = direction.normalize();

        double cos = direction.getX();
        double sin = direction.getY();
        for (int i = 0; i < x.length; i++) {
            double xi = x[i];
            double yi = y[i];
            x[i] = xi * cos + yi * sin;
            y[i] = yi * cos - xi * sin;
        }
    }

    private static double estimateLineLength(double[] x) {
        double sum = 0;
        for (double value : x) {
            sum += value;
        }
        double mean = sum / x.length;
        double sum2 = 0;
        for (double value : x) {
            sum2 += (value - mean) * (value - mean);
        }
        double variance = sum2 / x.length;
        return Math.sqrt(12 * variance);
    }

    private static List<StretchStatistics> detectLines(double[] x, double[] y, double minLineLength) {
        List<StretchStatistics> lines = new ArrayList<>();
        for (IndexRange stretch : detectStretches(x, minLineLength)) {
            StretchStatistics line = StretchStatistics.compute(x, y, stretch);
            if (line.isLine(minLineLength)) {
                lines.add(line);
            }
        }
        return lines;
    }

    // stretches of the track between the points where x
    // turns back by more than threshold
    private static List<IndexRange> detectStretches(double[] x, double threshold) {
        List<IndexRange> stretches = new ArrayList<>();
        int direction = 1;
        int first = 0;
        int last = 0; // last extreme of x in the current direction
        for (int i = 1; i < x.length; i++) {
            double toLast = direction * (x[i] - x[last]);
            if (toLast > 0) {
                last = i;
            } else if (toLast < -threshold) {
                stretches.add(new IndexRange(first, last + 1));
                direction = -direction;
                first = last;
                last = i;
            }
        }
        stretches.add(new IndexRange(first, last + 1));
        return stretches;
    }

    private static int nextNeighbor(List<StretchStatistics> lines, int index, double minOverlap) {
        StretchStatistics line = lines.get(index);
        for (int i = index + 1; i < lines.size(); i++) {
            StretchStatistics next = lines.get(i);
            double distance = next.yMean() - line.yMean();
            // lines apart along x are not neighbors;
            // lines within their deviations follow the same path
            if (line.overlap(next) > minOverlap && distance > line.yFitDeviation() + next.yFitDeviation()) {
                return i;
            }
        }
        return -1;
    }

    // stretch is aligned to the X axis
    private record StretchStatistics(
            double xMin,
            double xMax,
            double yMean,
            double yDeviation,
            double yFitDeviation
    ) {

        // share of the line length left out at each end of the track,
        // as it belongs to the turns
        private static final double MARGIN_RATIO = 0.1;

        // deviation of y that the points of a line may have,
        // share of the line length
        private static final double LINE_MAX_Y_DEVIATION_RATIO = 0.05;

        public static StretchStatistics compute(double[] x, double[] y, IndexRange range) {
            Check.notNull(range);

            int from = range.from();
            int to = range.to();

            double xMin = x[from];
            double xMax = x[from];
            for (int i = from + 1; i < to; i++) {
                xMin = Math.min(xMin, x[i]);
                xMax = Math.max(xMax, x[i]);
            }
            double margin = MARGIN_RATIO * (xMax - xMin);

            // sums of the coordinates relative to the first point,
            // as squares of large values lose precision
            int n = 0;
            double xSum = 0;
            double ySum = 0;
            double xxSum = 0;
            double xySum = 0;
            double yySum = 0;
            for (int i = from; i < to; i++) {
                if (x[i] >= xMin + margin && x[i] <= xMax - margin) {
                    double dx = x[i] - x[from];
                    double dy = y[i] - y[from];
                    n++;
                    xSum += dx;
                    ySum += dy;
                    xxSum += dx * dx;
                    xySum += dx * dy;
                    yySum += dy * dy;
                }
            }
            // a range without effective points gets no statistics (NaN)
            double xx = xxSum - xSum * xSum / n;
            double xy = xySum - xSum * ySum / n;
            double yy = yySum - ySum * ySum / n;
            double yMean = y[from] + ySum / n;
            double yDeviation = Math.sqrt(Math.max(0, yy) / n);
            double yFitDeviation = xx > 0
                    ? Math.sqrt(Math.max(0, yy - xy * xy / xx) / n)
                    : yDeviation;

            return new StretchStatistics(xMin, xMax, yMean, yDeviation, yFitDeviation);
        }

        public double length() {
            return xMax - xMin;
        }

        public double overlap(StretchStatistics other) {
            return Math.min(xMax, other.xMax) - Math.max(xMin, other.xMin);
        }

        public boolean isLine(double minLineLength) {
            // stretc without effective points fails y deviation check (as it is NaN)
            return length() > minLineLength
                    && yDeviation <= LINE_MAX_Y_DEVIATION_RATIO * length();
        }
    }
}
