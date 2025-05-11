package com.ugcs.gprvisualizer.app.yaml.data;

import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public class BaseData {

    private String header;
    private Integer index;
    private String regex;

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }
}
