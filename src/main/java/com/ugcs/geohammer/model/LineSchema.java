package com.ugcs.geohammer.model;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.math.SphericalMercator;
import com.ugcs.geohammer.math.PrincipalComponents;
import com.ugcs.geohammer.util.IndexRange;
import com.ugcs.geohammer.service.quality.LineComponents;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;

public class LineSchema {

    private final NavigableMap<Integer, IndexRange> ranges;

    private final NavigableMap<Integer, LineComponents> components;

    public LineSchema(List<GeoData> values) {
        ranges = getLineRanges(values);
        components = getLineComponents(values, ranges);
    }

    public NavigableMap<Integer, IndexRange> getRanges() {
        return ranges;
    }

    public NavigableMap<Integer, LineComponents> getComponents() {
        return components;
    }

    public Integer getPreviousLineIndex(int lineIndex) {
        return ranges.lowerKey(lineIndex);
    }

    public Integer getNextLineIndex(int lineIndex) {
        return ranges.higherKey(lineIndex);
    }

    public static NavigableMap<Integer, IndexRange> getLineRanges(List<? extends GeoData> values) {
        // line index -> [first index, last index)
        NavigableMap<Integer, IndexRange> ranges = new TreeMap<>();
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

            int valueLineIndex = value.getLineOrDefault(0);
            if (valueLineIndex != lineIndex) {
                if (i > lineStart) {
                    ranges.put(lineIndex, new IndexRange(lineStart, i));
                }
                lineIndex = valueLineIndex;
                lineStart = i;
            }
        }
        if (values.size() > lineStart) {
            ranges.put(lineIndex, new IndexRange(lineStart, values.size()));
        }
        return ranges;
    }

    public static NavigableMap<Integer, LineComponents> getLineComponents(
            List<GeoData> values, SortedMap<Integer, IndexRange> lineRanges) {
        NavigableMap<Integer, LineComponents> components = new TreeMap<>();
        if (values == null) {
            return components;
        }

        for (Map.Entry<Integer, IndexRange> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            IndexRange range = e.getValue();

            List<Point2D> points = new ArrayList<>();
            for (int i = range.from(); i < range.to(); i++) {
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
