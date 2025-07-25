package com.ugcs.gprvisualizer.app;

import com.ugcs.gprvisualizer.utils.Ticks;
import javafx.scene.chart.ValueAxis;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class SensorLineChartYAxis extends ValueAxis<Number> {

    private DecimalFormat formatter = new DecimalFormat();

    private int numTicks;

    public SensorLineChartYAxis(int numTicks) {
        this.numTicks = numTicks;
    }

    @Override
    protected void setRange(Object range, boolean animate) {
        AxisRange axisRange = (AxisRange) range;
        setLowerBound(axisRange.lowerBound);
        setUpperBound(axisRange.upperBound);
        currentLowerBound.set(axisRange.lowerBound);
    }

    @Override
    protected Object getRange() {
        AxisRange axisRange = new AxisRange();
        axisRange.lowerBound = getLowerBound();
        axisRange.upperBound = getUpperBound();
        return axisRange;
    }

    @Override
    protected List<Number> calculateMinorTickMarks() {
        return List.of();
    }

    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        AxisRange axisRange = (AxisRange)range;

        double tickUnit = Ticks.getPrettyTick(
                axisRange.lowerBound,
                axisRange.upperBound,
                numTicks);

        List<Number> ticks = new ArrayList<>();
        // lower bound
        ticks.add(axisRange.lowerBound);
        double lastAdded = axisRange.lowerBound;

        double tick = Math.ceil(axisRange.lowerBound / tickUnit) * tickUnit;
        if (tick == axisRange.lowerBound) {
            tick += tickUnit;
        }

        while (tick < axisRange.upperBound) {
            ticks.add(tick);
            lastAdded = tick;
            tick += tickUnit;
        }

        // upper bound
        if (axisRange.upperBound != lastAdded) {
            ticks.add(axisRange.upperBound);
        }

        // update formatter
        double valueRange = axisRange.upperBound - axisRange.lowerBound;
        int fractionalDigits = (int) Math.ceil(-Math.log10(valueRange)) + 1;
        fractionalDigits = Math.clamp(fractionalDigits, 0, 8);
        formatter.setMaximumFractionDigits(fractionalDigits);

        return ticks;
    }

    @Override
    protected String getTickMarkLabel(Number value) {
        return formatter.format(value);
    }

    static class AxisRange {
        double lowerBound;
        double upperBound;
    }
}
