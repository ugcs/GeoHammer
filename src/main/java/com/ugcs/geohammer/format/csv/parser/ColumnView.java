package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.util.Nulls;

import java.util.AbstractList;
import java.util.List;

public class ColumnView extends AbstractList<Number> {

    private final List<GeoData> values;

    private final String header;

    public ColumnView(List<GeoData> values, String header) {
        this.values = Nulls.toEmpty(values);
        this.header = header;
    }

    @Override
    public Number get(int index) {
        GeoData value = values.get(index);
        if (value == null) {
            return null;
        }
        return value.getNumber(header);
    }

    @Override
    public int size() {
        return values.size();
    }
}
