package com.ugcs.geohammer.chart.tool.projection.control;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Formats;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class SliderWithLabel extends VBox {

    private static final double SPACING = 4;

    private final String unit;

    private final Label value;

    private final Slider slider;

    public SliderWithLabel(String text, String unit, Range range) {
        this.unit = unit;
        slider = new Slider(
                range.getMin(),
                range.getMax(),
                (range.getMin() + range.getMax()) / 2);
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(false);
        slider.setBlockIncrement(1);

        Label label = new Label(text);
        value = new Label();

        Listeners.onChange(slider.valueProperty(), v -> updateValueLabel());

        setSpacing(SPACING);
        getChildren().addAll(
                new HBox(SPACING, label, Views.createSpacer(), value),
                slider);

        updateValueLabel();
    }

    private void updateValueLabel() {
        String s = Formats.prettyForRange(
                slider.getValue(),
                slider.getMin(),
                slider.getMax());
        if (!Strings.isNullOrEmpty(unit)) {
            s += " " + unit;
        }
        value.setText(s);
    }

    public Slider getSlider() {
        return slider;
    }
}
