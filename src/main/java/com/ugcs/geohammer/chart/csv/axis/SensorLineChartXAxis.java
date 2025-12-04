package com.ugcs.geohammer.chart.csv.axis;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.TraceUnit;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.TemplateSettings;
import com.ugcs.geohammer.model.event.TemplateUnitChangedEvent;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Templates;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class SensorLineChartXAxis extends ValueAxis<Number> {

    private static final DateTimeFormatter SECONDS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final DateTimeFormatter MILLISECONDS_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Model model;

    private final DecimalFormat formatter = new DecimalFormat("#0.00");

    private final SgyFile file;

    private final int numTicks;

    @Nullable
    private Button labelButton = null;

    public SensorLineChartXAxis(Model model, SgyFile file, int numTicks) {
        Check.notNull(file);

        this.model = model;
        this.file = file;
        this.numTicks = numTicks;

        initLabelButton();
        setLabel(getUnit().getLabel());
    }

    private void initLabelButton() {
        Label axisLabel = (Label) lookup(".axis-label");
        if (axisLabel != null) {
            axisLabel.setVisible(false);
        }

        labelButton = new Button();
        labelButton.setCursor(Cursor.HAND);
        labelButton.setOnAction(event -> onLabelClick());
        getChildren().add(labelButton);
    }

    public TraceUnit getUnit() {
        String templateName = Templates.getTemplateName(file);
        TemplateSettings templateSettings = model.getTemplateSettings();
        return templateSettings.getTraceUnit(templateName);
    }

    public void setUnit(TraceUnit traceUnit) {
        if (traceUnit == null ) {
            return;
        }

        String templateName = Templates.getTemplateName(file);
        TemplateSettings templateSettings = model.getTemplateSettings();
        templateSettings.setTraceUnit(templateName, traceUnit);

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

        if (labelButton != null) {
            labelButton.setVisible(this.isVisible() && this.isTickLabelsVisible());
            labelButton.setText(getUnitLabel(getUnit()));
            labelButton.autosize();
            labelButton.setLayoutX((getWidth() - labelButton.getWidth()) / 2);
            labelButton.setLayoutY(getHeight() - labelButton.getHeight() + 5);
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