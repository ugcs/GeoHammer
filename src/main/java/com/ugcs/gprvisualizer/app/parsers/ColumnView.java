package com.ugcs.gprvisualizer.app.parsers;

import com.ugcs.gprvisualizer.utils.Nulls;

import java.util.AbstractList;
import java.util.List;

public class ColumnView extends AbstractList<Number> {

    private final List<GeoData> values;

    private final int columnIndex;

    public ColumnView(List<GeoData> values, int columnIndex) {
        this.values = Nulls.toEmpty(values);
        this.columnIndex = columnIndex;
    }

    @Override
    public Number get(int index) {
        GeoData value = values.get(index);
        if (value == null) {
            return null;
        }
        return value.getNumber(columnIndex);
    }

    @Override
    public int size() {
        return values.size();
    }
}
