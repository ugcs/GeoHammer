package com.ugcs.gprvisualizer.app;

import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Strings;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for handling small value ranges (between 0 and 1).
 */
@ExtendWith({MockitoExtension.class})
public class PlotTest {

    /**
     * Test that the scale factor calculation properly handles values between 0 and 1.
     */
    @Test
    public void testScaleFactorForSmallValues() throws Exception {
        // Test with a value between 0 and 1
        double smallValue = 0.5;
        double scaleFactor = SensorLineChart.Plot.getScaleFactor(smallValue);
        assertEquals(1.0, scaleFactor, "Scale factor for 0.5 should be 1");
    }

    /**
     * Test that the value range calculation properly handles ranges between 0 and 1.
     */
    @Test
    public void testValueRangeForSmallRange() throws Exception {
        // Create a list of values between 0 and 1
        List<Number> smallValues = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);
        SensorLineChart.Plot plot = new SensorLineChart.Plot("test", Strings.empty(), Color.BLACK, smallValues);

        // Call the method
        Range range = plot.valueRange();

        assertEquals(0.0, range.getMin(), "Min value should be 0");
        assertEquals(1.0, range.getMax(), "Max value should be 1");

        assertEquals(0.1, plot.min(), "Series min value should be 0.1");
        assertEquals(0.9, plot.max(), "Series max value should be 0.9");
    }
}
