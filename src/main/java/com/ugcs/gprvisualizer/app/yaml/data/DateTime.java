package com.ugcs.gprvisualizer.app.yaml.data;

import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.jspecify.annotations.NullUnmarked;

import java.util.ArrayList;
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

    public List<String> getAllFormats() {
        if (Strings.isNullOrEmpty(format)) {
            return Nulls.toEmpty(formats);
        }
        if (Nulls.isNullOrEmpty(formats)) {
            return List.of(format);
        }
        List<String> combined = new ArrayList<>(formats.size() + 1);
        combined.add(format);
        combined.addAll(formats);
        return combined;
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
