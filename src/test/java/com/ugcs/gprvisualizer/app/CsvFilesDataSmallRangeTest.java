package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.commands.CommandRegistry;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import com.ugcs.gprvisualizer.math.LevelFilter;
import com.ugcs.gprvisualizer.utils.Range;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for handling small value ranges (between 0 and 1).
 */
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
public class CsvFilesDataSmallRangeTest {

    @Mock
    private Model model;

    @Mock
    private CsvFile csvFile;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PrefSettings settings;

    @Mock
    private SensorLineChart lineChart;

    private OptionPane optionPane;
    private RangeSlider rangeSlider;
    private Stage stage;

    @Start
    private void start(Stage stage) {
        this.stage = stage;

        // Create the OptionPane with mocked dependencies
        optionPane = new OptionPane(
            mock(MapView.class),
            mock(ProfileView.class),
            mock(CommandRegistry.class),
            model,
            mock(LevelFilter.class),
            settings,
            mock(Status.class),
            mock(Loader.class)
        );

        // Create the RangeSlider
        rangeSlider = new RangeSlider(0, 1, 0.1, 0.9);

        // Set up the scene
        VBox root = new VBox();
        root.getChildren().addAll(rangeSlider);
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Test that the scale factor calculation properly handles values between 0 and 1.
     */
    @Test
    public void testScaleFactorForSmallValues() throws Exception {
        // This test directly tests the getScaleFactor method in SensorLineChart
        // Since it's private, we need to use reflection to access it

        // Create an instance of SensorLineChart
        SensorLineChart chart = new SensorLineChart(model, eventPublisher, settings);

        // Get the getScaleFactor method using reflection
        Method getScaleFactorMethod =
            SensorLineChart.class.getDeclaredMethod("getScaleFactor", double.class);
        getScaleFactorMethod.setAccessible(true);

        // Test with a value between 0 and 1
        double smallValue = 0.5;
        double scaleFactor = (double) getScaleFactorMethod.invoke(chart, smallValue);

        assertEquals(1.0, scaleFactor, "Scale factor for 0.5 should be 1");
    }

    /**
     * Test that the value range calculation properly handles ranges between 0 and 1.
     */
    @Test
    public void testValueRangeForSmallRange() throws Exception {
        // This test directly tests the getValueRange method in SensorLineChart
        // Since it's private, we need to use reflection to access it

        // Create an instance of SensorLineChart
        SensorLineChart chart = new SensorLineChart(model, eventPublisher, settings);

        // Get the getValueRange method using reflection
        Method getValueRangeMethod =
            SensorLineChart.class.getDeclaredMethod("getValueRange", List.class, String.class);
        getValueRangeMethod.setAccessible(true);

        // Create a list of values between 0 and 1
        List<Number> smallValues = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);

        // Call the method
        Range range = (Range) getValueRangeMethod.invoke(chart, smallValues, "test");

        assertEquals(0.0, range.getMin(), "Min value should be 0");
        assertEquals(1.0, range.getMax(), "Max value should be 1");

        // Check the semantic min/max values directly in the maps using reflection
        Field semanticMinValuesField = SensorLineChart.class.getDeclaredField("semanticMinValues");
        semanticMinValuesField.setAccessible(true);
        Map<String, Double> semanticMinValues = (Map<String, Double>) semanticMinValuesField.get(chart);

        Field semanticMaxValuesField = SensorLineChart.class.getDeclaredField("semanticMaxValues");
        semanticMaxValuesField.setAccessible(true);
        Map<String, Double> semanticMaxValues = (Map<String, Double>) semanticMaxValuesField.get(chart);

        assertEquals(0.1, semanticMinValues.get("test"), "Semantic min value should be 0.1");
        assertEquals(0.9, semanticMaxValues.get("test"), "Semantic max value should be 0.9");
    }

    /**
     * Test that demonstrates the issue with the gridding range slider when values are between 0 and 1.
     * This test simulates the behavior of the updateGriddingMinMaxPreserveUserRange method in OptionPane.
     */
    @Test
    public void testGriddingRangeSliderWithSmallRange() throws Exception {
        // Mock the behavior of SensorLineChart.getSemanticMinValue() and getSemanticMaxValue()
        when(lineChart.getSemanticMinValue()).thenReturn(0.1);
        when(lineChart.getSemanticMaxValue()).thenReturn(0.9);

        // Set the selected file using reflection
        Field selectedFileField = OptionPane.class.getDeclaredField("selectedFile");
        selectedFileField.setAccessible(true);
        selectedFileField.set(optionPane, csvFile);

        // Set the RangeSlider in the OptionPane using reflection
        Field griddingRangeSliderField = OptionPane.class.getDeclaredField("griddingRangeSlider");
        griddingRangeSliderField.setAccessible(true);
        griddingRangeSliderField.set(optionPane, rangeSlider);

        // Mock the model.getChart() method to return our mocked lineChart
        when(model.getCsvChart(any(CsvFile.class))).thenReturn(Optional.of(lineChart));

        // Call the updateGriddingMinMaxPreserveUserRange method using reflection
        Method updateMethod = OptionPane.class.getDeclaredMethod("updateGriddingMinMaxPreserveUserRange");
        updateMethod.setAccessible(true);
        updateMethod.invoke(optionPane);

        // Wait for JavaFX events to be processed
        WaitForAsyncUtils.waitForFxEvents();

        // Verify that the range slider's min and max values are set correctly
        assertTrue(rangeSlider.getMin() <= 0.1, "Min value should be less or equal to 0.1");
        assertTrue(rangeSlider.getMax() >= 0.9, "Max value should be greater or equal to 0.9");
    }
}
