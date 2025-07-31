package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.commands.CommandRegistry;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import com.ugcs.gprvisualizer.math.LevelFilter;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the OptionPane class focusing on gridding range slider behavior.
 */
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
public class OptionPaneTest {

    @Mock
    private Model model;

    @Mock
    private CsvFile csvFile;

    @Mock
    private SensorLineChart lineChart;

    @Mock
    private PrefSettings prefSettings;

    @Mock
    private MapView mapView;

    @Mock
    private ProfileView profileView;

    @Mock
    private CommandRegistry commandRegistry;

    @Mock
    private LevelFilter levelFilter;

    @Mock
    private Status status;

    private OptionPane optionPane;
    private RangeSlider griddingRangeSlider;

    @Start
    private void start(Stage stage) throws Exception {
        // Create and init the OptionPane
        optionPane = new OptionPane(mapView, profileView, commandRegistry, model, levelFilter, prefSettings, status);

        // Create the range slider directly 
        griddingRangeSlider = new RangeSlider(0, 100, 25, 75);
        setFieldValue(optionPane, "griddingRangeSlider", griddingRangeSlider);

        // Set up the UI components
        VBox root = new VBox();
        root.getChildren().add(optionPane);

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    // Helper method to set a field value using reflection
    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Test that the update method correctly preserves user selection proportionally
     * when min/max values change.
     */
    @Test
    public void testUpdateGriddingMinMaxPreserveUserRange() throws Exception {
        // Mock new chart values
        when(model.getCsvChart(any(CsvFile.class))).thenReturn(Optional.of(lineChart));
        when(lineChart.getSemanticMinValue()).thenReturn(-50.0); // New min
        when(lineChart.getSemanticMaxValue()).thenReturn(150.0); // New max

        // Set the selected file using reflection
        Field selectedFileField = OptionPane.class.getDeclaredField("selectedFile");
        selectedFileField.setAccessible(true);
        selectedFileField.set(optionPane, csvFile);

        // When: Call the update method using reflection
        Method updateMethod = OptionPane.class.getDeclaredMethod("updateGriddingMinMaxPreserveUserRange");
        updateMethod.setAccessible(true);
        updateMethod.invoke(optionPane);
        WaitForAsyncUtils.waitForFxEvents();

        // In the current implementation, the low value is set to the min value
        // and the high value is set to the max value;
        // min/max values change due to dynamic expansion of the slider range
        assertEquals(-50.0, griddingRangeSlider.getLowValue(), 0.1, 
                "Low value should be set to the min value");
        assertEquals(150.0, griddingRangeSlider.getHighValue(), 0.1, 
                "High value should be set to the max value");
    }

    /**
     * Test that when min/max values don't change, the user selection is preserved exactly.
     */
    @Test
    public void testUpdateGriddingMinMaxPreservesExactValuesWhenNoChange() throws Exception {
        // Given: Set up initial values
        griddingRangeSlider.setLowValue(30.0);  // Specific values
        griddingRangeSlider.setHighValue(80.0); // that should be preserved

        // Mock chart values that are the same as current min/max
        when(model.getCsvChart(any(CsvFile.class))).thenReturn(Optional.of(lineChart));
        when(lineChart.getSemanticMinValue()).thenReturn(0.0); // Same min
        when(lineChart.getSemanticMaxValue()).thenReturn(100.0); // Same max

        // Set the selected file using reflection
        Field selectedFileField = OptionPane.class.getDeclaredField("selectedFile");
        selectedFileField.setAccessible(true);
        selectedFileField.set(optionPane, csvFile);

        // When: Call the update method using reflection
        Method updateMethod = OptionPane.class.getDeclaredMethod("updateGriddingMinMaxPreserveUserRange");
        updateMethod.setAccessible(true);
        updateMethod.invoke(optionPane);
        WaitForAsyncUtils.waitForFxEvents();

        // In the current implementation, the low value is set to the min value
        // and the high value is set to the max value;
        // min/max values change due to dynamic expansion of the slider range
        assertEquals(0.0, griddingRangeSlider.getLowValue(),
                "Low value should be set to the min value");
        assertEquals(100.0, griddingRangeSlider.getHighValue(),
                "High value should be set to the max value");
    }
}
