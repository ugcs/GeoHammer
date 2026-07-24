package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Formats;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class SliderWithLabel extends VBox {

    private final Unit unit;

    private final Label value;

    private final Slider slider;

    public SliderWithLabel(String text, Unit unit, Range range) {
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

        setSpacing(Views.LABEL_SPACING);
        getChildren().addAll(
                new HBox(Views.LABEL_SPACING, label, Views.createSpacer(), value),
                slider);

        updateValueLabel();
    }

    private String formatValue(Number value) {
        return Formats.prettyForRange(
                value,
                slider.getMin(),
                slider.getMax());
    }

    private void updateValueLabel() {
        String text = unit != null
                ? unit.format(slider.getValue(), this::formatValue)
                : formatValue(slider.getValue());
        value.setText(text);
    }

    public Slider getSlider() {
        return slider;
    }
}
