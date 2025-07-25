package com.ugcs.gprvisualizer.app;

import com.ugcs.gprvisualizer.app.commands.CommandRegistry;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import com.ugcs.gprvisualizer.math.LevelFilter;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.controlsfx.control.RangeSlider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.utils.FXUtils;
import org.testfx.util.WaitForAsyncUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for the gridding range formatting behavior.
 * Verifies that the correct number of decimal places is used based on the range size.
 */
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
public class GriddingRangeFormattingTest {

    @Mock
    private Model model;

    @Mock
    private PrefSettings prefSettings;

    private OptionPane optionPane;
    private RangeSlider griddingRangeSlider;
    private Method setFormattedValueMethod;
    private Label testLabel;
    private Stage stage;

    @Start
    private void start(Stage stage) {
        this.stage = stage;

        // Create the test label
        testLabel = new Label();

        // Set up the scene
        VBox root = new VBox();
        root.getChildren().add(testLabel);
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    @BeforeEach
    public void setUp() throws Exception {
        // Create an instance of OptionPane with mocked dependencies
        optionPane = new OptionPane(
            mock(MapView.class),
            mock(ProfileView.class),
            mock(CommandRegistry.class),
            model,
            mock(LevelFilter.class),
            prefSettings
        );

        // Create a RangeSlider and set it in the OptionPane instance
        griddingRangeSlider = new RangeSlider();
        Field griddingRangeSliderField = OptionPane.class.getDeclaredField("griddingRangeSlider");
        griddingRangeSliderField.setAccessible(true);
        griddingRangeSliderField.set(optionPane, griddingRangeSlider);

        // Get the setFormattedValue method using reflection
        setFormattedValueMethod = OptionPane.class.getDeclaredMethod("setFormattedValue", Number.class, String.class, Label.class);
        setFormattedValueMethod.setAccessible(true);
    }


    /**
     * Test that values are formatted with 2 decimal places when range is less than 10.
     */
    @Test
    public void testFormattingForRangeLessThan10() throws Exception {
        // Given: A range less than 10
        double min = 1.0;
        double max = 9.0;
        griddingRangeSlider.setMin(min);
        griddingRangeSlider.setMax(max);

        // When: Format a value
        double value = 1.23456;

        // Format with "Min: " prefix
        String formattedMin = FXUtils.runAndWait(() -> {
                    try {
                        setFormattedValueMethod.invoke(optionPane, value, "Min: ", testLabel);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    return testLabel.getText();
                });

        // Format with "Max: " prefix
        String formattedMax = FXUtils.runAndWait(() -> {
                    try {
                        setFormattedValueMethod.invoke(optionPane, value, "Max: ", testLabel);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    return testLabel.getText();
                });

        // Then: Verify that values are formatted with 2 decimal places
        assertEquals("Min: 1.23", formattedMin, "Value should be formatted with 2 decimal places for range < 10");
        assertEquals("Max: 1.23", formattedMax, "Value should be formatted with 2 decimal places for range < 10");
    }

    /**
     * Test that values are formatted with 1 decimal place when range is less than 100 but greater than or equal to 10.
     */
    @Test
    public void testFormattingForRangeLessThan100() throws Exception {
        // Given: A range less than 100 but >= 10
        double min = 10.0;
        double max = 90.0;
        griddingRangeSlider.setMin(min);
        griddingRangeSlider.setMax(max);

        // When: Format a value
        double value = 12.34567;

        // Format with "Min: " prefix
        String formattedMin = FXUtils.runAndWait(() -> {
                    try {
                        setFormattedValueMethod.invoke(optionPane, value, "Min: ", testLabel);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    return testLabel.getText();
                });


        // Format with "Max: " prefix
        String formattedMax = FXUtils.runAndWait(() -> {
                    try {
                        setFormattedValueMethod.invoke(optionPane, value, "Max: ", testLabel);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    return testLabel.getText();
                });

        // Then: Verify that values are formatted with 1 decimal place
        assertEquals("Min: 12.3", formattedMin, "Value should be formatted with 1 decimal place for 10 <= range < 100");
        assertEquals("Max: 12.3", formattedMax, "Value should be formatted with 1 decimal place for 10 <= range < 100");
    }

    /**
     * Test that values are formatted as integers when range is greater than or equal to 100.
     */
    @Test
    public void testFormattingForRangeGreaterThanOrEqual100() throws Exception {
        // Given: A range >= 100
        double min = 0.0;
        double max = 200.0;
        griddingRangeSlider.setMin(min);
        griddingRangeSlider.setMax(max);

        // When: Format a value
        double value = 123.456;

        // Format with "Min: " prefix
        String formattedMin = FXUtils.runAndWait(() -> {
                    try {
                        setFormattedValueMethod.invoke(optionPane, value, "Min: ", testLabel);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    return testLabel.getText();
                });

        // Format with "Max: " prefix
        String formattedMax = FXUtils.runAndWait(() -> {
                    try {
                        setFormattedValueMethod.invoke(optionPane, value, "Max: ", testLabel);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                    return testLabel.getText();
                });

        // Then: Verify that values are formatted as integers
        assertEquals("Min: 123", formattedMin, "Value should be formatted as integer for range >= 100");
        assertEquals("Max: 123", formattedMax, "Value should be formatted as integer for range >= 100");
    }

}
