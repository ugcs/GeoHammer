package com.ugcs.gprvisualizer.app.axis;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.TraceUnit;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.service.TemplateSettingsModel;
import com.ugcs.gprvisualizer.event.TemplateUnitChangedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import com.ugcs.gprvisualizer.utils.Templates;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

@Component
public class SensorLineChartXAxis extends ValueAxis<Number> {

    private static final Logger log = LoggerFactory.getLogger(SensorLineChartXAxis.class);

    private static final DateTimeFormatter SECONDS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final DateTimeFormatter MILLISECONDS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Model model;

    private final TemplateSettingsModel templateSettingsModel;

    private final DecimalFormat formatter = new DecimalFormat();

    private final CsvFile file;

    private final int numTicks;

    @Nullable
    private Button labelButton = null;

    public SensorLineChartXAxis(Model model, TemplateSettingsModel templateSettingsModel, CsvFile file, int numTicks) {
        Check.notNull(file);

        this.model = model;
        this.templateSettingsModel = templateSettingsModel;
        this.file = file;
        this.numTicks = numTicks;

        setLabel(getUnit().getLabel());
    }

    public TraceUnit getUnit() {
        String templateName = Templates.getTemplateName(file);
        return templateSettingsModel.getTraceUnit(templateName);
    }

    public void setUnit(TraceUnit traceUnit) {
        if (traceUnit == null ) {
            return;
        }

        String templateName = Templates.getCsvTemplateName(file);
        templateSettingsModel.setTraceUnit(templateName, traceUnit);

        setLabel(traceUnit.getLabel());

        if (labelButton != null) {
            labelButton.setText(traceUnit.getLabel());
            labelButton.applyCss();
            labelButton.autosize();
        }

        invalidateRange();
    }

    @Override
    protected String getTickMarkLabel(Number value) {
        List<GeoData> values = Nulls.toEmpty(file.getGeoData());

        int traceIndex = value.intValue();
        if (traceIndex < 0 || traceIndex >= values.size()) {
            return Strings.empty();
        }

        TraceUnit traceUnit = getUnit();
        switch (traceUnit) {
            case METERS, KILOMETERS, MILES, FEET -> {
                double distance = file.getDistanceAtTrace(traceIndex);
                return formatter.format(TraceUnit.convert(distance, traceUnit));
            }
            case TIME -> {
                LocalDateTime dt = values.get(traceIndex).getDateTime();
                if (dt == null) {
                    return Strings.empty();
                }

                double visibleRange = getUpperBound() - getLowerBound();
                boolean showMilliseconds = visibleRange < (double) file.numTraces() / numTicks;

                DateTimeFormatter formatter = showMilliseconds
                        ? MILLISECONDS_FORMATTER
                        : SECONDS_FORMATTER;
                return dt.format(formatter);
            }
            case TRACES -> {
                return String.format("%1$3s", traceIndex);
            }
            default -> {
                return Strings.empty();
            }
        }
    }

    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        AxisRange r = (AxisRange) range;
        double lower = r.lowerBound();
        double upper = r.upperBound();

        List<Number> tickValues = new ArrayList<>();

        if (upper <= lower) {
            return tickValues;
        }

        double step = (upper - lower) / numTicks;

        for (int i = 0; i <= numTicks; i++) {
            double tickValue = lower + i * step;
            tickValues.add(tickValue);
        }

        return tickValues;
    }

    @EventListener
    public void onTraceCut(WhatChanged event) {
        if (event.isTraceCut()) {
            Platform.runLater(this::requestAxisLayout);
        }
    }

    @Override
    protected Object getRange() {
        return new AxisRange(getLowerBound(), getUpperBound());
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        AxisRange axisRange = (AxisRange) range;
        setLowerBound(axisRange.lowerBound());
        setUpperBound(axisRange.upperBound());
    }

    @Override
    protected Object autoRange(double minValue, double maxValue, double length, double labelSize) {
        return new AxisRange(minValue, maxValue);
    }

    @Override
    protected List<Number> calculateMinorTickMarks() {
        return List.of();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();

        Label axisLabel = (Label) lookup(".axis-label");
        if (axisLabel != null && !axisLabel.getStyleClass().contains("clickable-label") && labelButton == null) {
            axisLabel.setVisible(false);

            labelButton = new Button(axisLabel.getText());
            labelButton.getStyleClass().add("clickable-label");
            labelButton.setCursor(Cursor.HAND);
            labelButton.setVisible(this.isVisible() && this.isTickLabelsVisible());

            labelButton.setOnAction(event -> onLabelClick());

            getChildren().add(labelButton);
        } else if (labelButton != null) {
            labelButton.setVisible(this.isVisible() && this.isTickLabelsVisible());
            getChildren().remove(labelButton);
            labelButton.setText(getUnitLabel(getUnit()));
            labelButton.autosize();

            labelButton.setLayoutX((getWidth() - labelButton.getWidth()) / 2);
            labelButton.setLayoutY(getHeight() - labelButton.getHeight() + 5);
            getChildren().add(labelButton);
        }
    }

    // Change "traces" to "measurements" for better clarity
    private String getUnitLabel(TraceUnit unit) {
        if (unit == null) {
            return Strings.empty();
        }
        if (unit == TraceUnit.TRACES) {
            return "measurements";
        }
        return unit.getLabel();
    }

    private void onLabelClick() {
        TraceUnit traceUnit = getUnit();
        TraceUnit[] traceUnits = TraceUnit.values();

        int nextIndex = (traceUnit.ordinal() + 1) % traceUnits.length;
        TraceUnit nextTraceUnit = traceUnits[nextIndex];
        setUnit(nextTraceUnit);

        notifyTemplateUnitChange(nextTraceUnit);
    }

    private void notifyTemplateUnitChange(TraceUnit traceUnit) {
        Platform.runLater(() ->
                model.publishEvent(new TemplateUnitChangedEvent(this, file, traceUnit))
        );
    }
}