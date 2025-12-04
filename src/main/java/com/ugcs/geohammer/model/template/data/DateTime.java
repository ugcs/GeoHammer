package com.ugcs.geohammer.model.template.data;

import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.NullUnmarked;

import java.util.ArrayList;
import java.util.List;

@NullUnmarked
public class DateTime extends BaseData {

    private String format;

    private List<String> formats;

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
}
