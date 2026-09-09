package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.util.Ticks;
import com.ugcs.geohammer.util.Unit;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public abstract class BaseSlider extends HBox {

    protected final Slider slider;

    protected final Label label;

    protected final String name;

    protected final Unit unit;

    private final DecimalFormat format = new DecimalFormat(
            "0.#", DecimalFormatSymbols.getInstance(Locale.US));

    public BaseSlider(String name, Unit unit, Range range, double tickUnit) {
        this.name = name;
        this.unit = unit;

        slider = new Slider(range.getMin(), range.getMax(), range.getMin());
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(tickUnit);
        slider.setMinorTickCount(0);
        slider.setPrefWidth(200);
        slider.setBlockIncrement(1);

        Listeners.onChange(slider.valueProperty(), v -> {
            updateText(v);
            onValueChanged(v);
        });

        label = new Label(name);

        setSpacing(2.5);
        setPadding(new Insets(4, 8, 4, 0));
        setPrefWidth(Double.MAX_VALUE);

        getChildren().addAll(label, Views.createSpacer(), slider);

        updateText(slider.getValue());
    }

    public Slider getSlider() {
        return slider;
    }

    public Label getLabel() {
        return label;
    }

    private void updateText(Number value) {
        if (value == null) {
            label.setText(name);
        } else {
            String valueString = unit != null
                    ? unit.format(value, format::format)
                    : value.toString();
            label.setText(name + ":\n" + valueString);
        }
    }

    public abstract void onValueChanged(Number value);

	public abstract void update();
}
