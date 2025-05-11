package com.ugcs.gprvisualizer.app.yaml.data;

import org.jspecify.annotations.NullUnmarked;

import java.util.List;

@NullUnmarked
public class DateTime extends BaseData {

    private String format;
    private List<String> formats;
    private Type type;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public List<String> getFormats() {
        return formats;
    }

    public void setFormats(List<String> formats) {
        this.formats = formats;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public enum Type {
        UTC,
        GPST
    }
    
}
