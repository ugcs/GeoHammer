package com.ugcs.geohammer.model.template.data;

import org.jspecify.annotations.NullUnmarked;

@NullUnmarked
public class BaseData {

    public static final int DEFAULT_DECIMALS = 2;

    private String header;

    private Integer index;

    private String regex;

    private int decimals = DEFAULT_DECIMALS;

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

    public int getDecimals() {
        return decimals;
    }

    public void setDecimals(int decimals) {
        this.decimals = decimals;
    }
}
