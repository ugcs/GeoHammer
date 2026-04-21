package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Strings;

public class WindowProperties {

    private String title = Strings.empty();

    private String style = Strings.empty();

    private double width;

    private double height;

    private double minWidth;

    private double minHeight;

    public WindowProperties(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public WindowProperties withTitle(String title) {
        this.title = title;
        return this;
    }

    public String style() {
        return style;
    }

    public WindowProperties withStyle(String style) {
        this.style = style;
        return this;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public WindowProperties withSize(double width, double height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public double minWidth() {
        return minWidth;
    }

    public double minHeight() {
        return minHeight;
    }

    public WindowProperties withMinSize(double minWidth, double minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        return this;
    }
}
