package com.ugcs.geohammer.model;

import com.ugcs.geohammer.util.Check;

public class Column {

    private final String header;

    private String semantic;

    private String unit;

    private boolean display = true;

    private boolean readOnly = false;

    public Column(String header) {
        Check.notNull(header);

        this.header = header;
    }

    public static Column copy(Column column) {
        if (column == null) {
            return null;
        }
        Column copy = new Column(column.header);
        copy.semantic = column.semantic;
        copy.unit = column.unit;
        copy.display = column.display;
        return copy;
    }

    public String getHeader() {
        return header;
    }

    public String getSemantic() {
        return semantic;
    }

    public void setSemantic(String semantic) {
        this.semantic = semantic;
    }

    public Column withSemantic(String semantic) {
        setSemantic(semantic);
        return this;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Column withUnit(String unit) {
        setUnit(unit);
        return this;
    }

    public boolean isDisplay() {
        return display;
    }

    public void setDisplay(boolean display) {
        this.display = display;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public Column withDisplay(boolean display) {
        setDisplay(display);
        return this;
    }
}
