package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import javafx.stage.StageStyle;

public class WindowProperties {

    private StageStyle style = StageStyle.UTILITY;

    private String title = Strings.empty();

    private double width;

    private double height;

    private double minWidth;

    private double minHeight;


    public WindowProperties(String title) {
        this.title = title;
    }

    public StageStyle style() {
        return style;
    }

    public WindowProperties withStyle(StageStyle style) {
        this.style = Check.notNull(style);
        return this;
    }

    public String title() {
        return title;
    }

    public WindowProperties withTitle(String title) {
        this.title = title;
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
