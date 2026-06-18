package com.ugcs.geohammer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ugcs.geohammer.format.GeoData;

class LineSchemaTest {

    private ColumnSchema schema;

    @BeforeEach
    void setUp() {
        schema = new ColumnSchema();
        schema.addColumn(new Column("line").withSemantic(Semantic.LINE.getName()));
    }

    private GeoData row(int line) {
        GeoData value = new GeoData(schema);
        value.setLine(line);
        return value;
    }

    private List<GeoData> lines(int... lineNumbers) {
        List<GeoData> values = new ArrayList<>();
        for (int line : lineNumbers) {
            values.add(row(line));
        }
        return values;
    }

    private List<Integer> lineOf(List<GeoData> values) {
        List<Integer> result = new ArrayList<>();
        for (GeoData value : values) {
            result.add(value.getLine());
        }
        return result;
    }

    @Test
    void mergesTrailingSinglePointIntoPreviousLine() {
        List<GeoData> values = lines(0, 0, 0, 1);

        int merged = LineSchema.mergeSinglePointLines(values);

        assertEquals(1, merged);
        assertEquals(List.of(0, 0, 0, 0), lineOf(values));
    }

    @Test
    void mergesLeadingSinglePointIntoNextLine() {
        List<GeoData> values = lines(0, 1, 1, 1);

        int merged = LineSchema.mergeSinglePointLines(values);

        assertEquals(1, merged);
        assertEquals(List.of(1, 1, 1, 1), lineOf(values));
    }

    @Test
    void mergesAdjacentSinglePointLinesTogether() {
        List<GeoData> values = lines(0, 1, 2, 2);

        int merged = LineSchema.mergeSinglePointLines(values);

        assertEquals(2, merged);
        assertEquals(List.of(1, 1, 2, 2), lineOf(values));
    }

    @Test
    void keepsMultiPointLinesUnchanged() {
        List<GeoData> values = lines(0, 0, 1, 1);

        int merged = LineSchema.mergeSinglePointLines(values);

        assertEquals(0, merged);
        assertEquals(List.of(0, 0, 1, 1), lineOf(values));
    }

    @Test
    void leavesLoneSinglePointWhenThereIsNoNeighbour() {
        List<GeoData> values = lines(5);

        int merged = LineSchema.mergeSinglePointLines(values);

        assertEquals(0, merged);
        assertEquals(List.of(5), lineOf(values));
    }

    @Test
    void handlesNullAndEmptyInput() {
        assertEquals(0, LineSchema.mergeSinglePointLines(null));
        assertEquals(0, LineSchema.mergeSinglePointLines(new ArrayList<>()));
    }
}
