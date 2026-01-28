package com.ugcs.geohammer.view;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Ticks;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

abstract public class ExpandableSlider extends HBox {

    // when slider gets to an edge in a given factor
    // of its length it would shift its range toward the edge
    private static final float EXPAND_THRESHOLD = 0.2f;

    private final String name;

    private final Range range;

    private final Label label;

    private final Slider slider;

    public ExpandableSlider(String name, int defaultValue, int width) {
        this(name, defaultValue, width, null);
    }

    public ExpandableSlider(String name, int defaultValue, int width, Range range) {
        this.name = name;
        this.range = range;

        label = new Label(name + ": " + defaultValue);

        slider = new Slider();
        slider.setPrefWidth(200);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(Ticks.getPrettyTick(0, width, 5));
        slider.setBlockIncrement(1);
        reset(defaultValue, width);

        // on mouse release
        slider.valueProperty().addListener(this::onValueChanged);
        slider.setOnMouseReleased(this::onMouseReleased);

        Button resetButton = Views.createGlyphButton("↺", 16, 16);
        resetButton.setOnAction(e -> reset(defaultValue, width));

        setSpacing(2.5);
        setPadding(new Insets(4, 8, 4, 0));
        setPrefWidth(Double.MAX_VALUE);
        getChildren().addAll(label, Views.createSpacer(), slider, resetButton);
    }

    public void reset(int value, int width) {
        if (range != null) {
            value = Math.clamp(value, (int) range.getMin(), (int) range.getMax());
            width = Math.min(width, (int) range.getMax() - (int) range.getMin());
        }
        int halfWidth = width / 2;
        int min = value - halfWidth;
        if (range != null) {
            min = Math.max(min, (int) range.getMin());
        }
        slider.setMin(min);
        slider.setMax(min + width);
        slider.setValue(value);
    }

    public void reset(int value) {
        reset(value, (int) slider.getMax() - (int) slider.getMin());
    }

    private void onValueChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        int value = newValue.intValue();
        if (value == oldValue.intValue()) {
            return;
        }
        label.setText(name + ": " + value);
        onValue(value);
    }

    private void onMouseReleased(MouseEvent event) {
        // expand slider range when value is close to edges
        double width = slider.getMax() - slider.getMin();
        double position = slider.getValue() - slider.getMin();
        double margin = EXPAND_THRESHOLD * width;
        int offset = 0;
        if (position < margin) {
            offset = -(int) margin;
            if (range != null) {
                offset = Math.max(offset, (int) range.getMin() - (int) slider.getMin());
            }
        }
        if (position > width - margin) {
            offset = (int) margin;
            if (range != null) {
                offset = Math.min(offset, (int) range.getMax() - (int) slider.getMax());
            }
        }
        if (offset != 0) {
            slider.setMin((int) slider.getMin() + offset);
            slider.setMax((int) slider.getMax() + offset);
        }
    }

    public int getValue() {
        return (int) slider.getValue();
    }

    abstract public void onValue(int value);
}
