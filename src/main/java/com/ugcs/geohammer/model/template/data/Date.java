package com.ugcs.geohammer.model.template.data;

import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public class Date extends DateTime {
    
    private Source source;

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public enum Source {
        Column,
        FileName
    }
}