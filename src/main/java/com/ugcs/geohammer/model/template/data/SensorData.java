package com.ugcs.geohammer.model.template.data;

import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public class SensorData extends BaseData {

    String format;

    String semantic;

    String units;

    private boolean readOnly = false;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSemantic() {
        return semantic;
    }

    public void setSemantic(String semantic) {
        this.semantic = semantic;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
