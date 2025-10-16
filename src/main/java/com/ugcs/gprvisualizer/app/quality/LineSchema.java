package com.ugcs.gprvisualizer.app.quality;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.SphericalMercator;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.math.PrincipalComponents;
import com.ugcs.gprvisualizer.utils.Range;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class LineSchema {

    private final TreeMap<Integer, Range> ranges;

    private final TreeMap<Integer, LineComponents> components;

    public LineSchema(List<GeoData> values, String lineHeader) {
        ranges = getLineRanges(values, lineHeader);
        components = getLineComponents(values, ranges);
    }

    public SortedMap<Integer, Range> getRanges() {
        return ranges;
    }

    public SortedMap<Integer, LineComponents> getComponents() {
        return components;
    }

    public Integer getPreviousLineIndex(int lineIndex) {
        return ranges.lowerKey(lineIndex);
    }

    public Integer getNextLineIndex(int lineIndex) {
        return ranges.higherKey(lineIndex);
    }

    public static TreeMap<Integer, Range> getLineRanges(List<? extends GeoData> values, String lineHeader) {
        // line index -> [first index, last index]
        TreeMap<Integer, Range> ranges = new TreeMap<>();
        if (values == null) {
            return ranges;
        }

        int lineIndex = 0;
        int lineStart = 0;
        for (int i = 0; i < values.size(); i++) {
            GeoData value = values.get(i);
            if (value == null) {
                continue;
            }

            int valueLineIndex = value.getInt(lineHeader).orElse(0);
            if (valueLineIndex != lineIndex) {
                if (i > lineStart) {
                    ranges.put(lineIndex, new Range(lineStart, i - 1));
                }
                lineIndex = valueLineIndex;
                lineStart = i;
            }
        }
        if (values.size() > lineStart) {
            ranges.put(lineIndex, new Range(lineStart, values.size() - 1));
        }
        return ranges;
    }

    public static TreeMap<Integer, LineComponents> getLineComponents(
            List<GeoData> values, SortedMap<Integer, Range> lineRanges) {
        TreeMap<Integer, LineComponents> components = new TreeMap<>();
        if (values == null) {
            return components;
        }

        for (Map.Entry<Integer, Range> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            Range range = e.getValue();

            List<Point2D> points = new ArrayList<>();
            for (int i = range.getMin().intValue(); i <= range.getMax().intValue(); i++) {
                GeoData value = values.get(i);
                LatLon latlon = new LatLon(value.getLatitude(), value.getLongitude());
                Point2D projected = SphericalMercator.project(latlon);
                points.add(projected);
            }

            PrincipalComponents.Components2 lineComponents = PrincipalComponents.findComponents(points);
            components.put(lineIndex, new LineComponents(
                    lineComponents.centroid(),
                    lineComponents.primary()));
        }
        return components;
    }
}
