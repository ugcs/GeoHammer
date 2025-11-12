package com.ugcs.geohammer.map.layer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.lang.reflect.Method;

/**
 * Tests for the hill-shading functionality in GridLayer.
 */
public class HillShadingTest {

    /**
     * Test that the calculateHillShading method returns correct illumination values.
     * This test uses reflection to access the private method.
     */
    @Test
    public void testCalculateHillShading() throws Exception {
        // Get the private method using reflection
        Method calculateHillShadingMethod = GridLayer.class.getDeclaredMethod(
                "calculateHillShading", float[][].class, int.class, int.class, double.class, double.class);
        calculateHillShadingMethod.setAccessible(true);

        // Create a simple grid with a slope
        float[][] gridData = new float[3][3];
        gridData[0][0] = 0.0f;
        gridData[0][1] = 0.0f;
        gridData[0][2] = 0.0f;
        gridData[1][0] = 0.5f;
        gridData[1][1] = 0.5f;
        gridData[1][2] = 0.5f;
        gridData[2][0] = 1.0f;
        gridData[2][1] = 1.0f;
        gridData[2][2] = 1.0f;

        // Test with different azimuth and altitude values
        double illumination1 = (double) calculateHillShadingMethod.invoke(null, gridData, 1, 1, 315.0, 45.0);
        System.out.println("[DEBUG_LOG] Illumination with azimuth=315, altitude=45: " + illumination1);
        assertTrue(illumination1 >= 0.0 && illumination1 <= 1.0, "Illumination should be in range [0, 1]");

        double illumination2 = (double) calculateHillShadingMethod.invoke(null, gridData, 1, 1, 135.0, 45.0);
        System.out.println("[DEBUG_LOG] Illumination with azimuth=135, altitude=45: " + illumination2);
        assertTrue(illumination2 >= 0.0 && illumination2 <= 1.0, "Illumination should be in range [0, 1]");

        // Test edge case
        double illuminationEdge = (double) calculateHillShadingMethod.invoke(null, gridData, 0, 0, 315.0, 45.0);
        System.out.println("[DEBUG_LOG] Illumination at edge: " + illuminationEdge);
        assertEquals(1.0, illuminationEdge, "Edge cells should have default illumination of 1.0");
    }

    /**
     * Test that the getColorForValue method returns correct base colors.
     * This test uses reflection to access the private method.
     */
    @Test
    public void testGetColorForValue() throws Exception {
        // Get the private method using reflection
        Method getColorForValueMethod = GridLayer.class.getDeclaredMethod(
                "getColorForValue", double.class, double.class, double.class);
        getColorForValueMethod.setAccessible(true);

        // Test with different values
        Color color1 = (Color) getColorForValueMethod.invoke(null, 0.0, 0.0, 1.0);
        System.out.println("[DEBUG_LOG] Color for value=0.0, min=0.0, max=1.0: " + colorToString(color1));
        assertNotEquals(Color.WHITE, color1, "Color should not be white for minimum value");

        Color color2 = (Color) getColorForValueMethod.invoke(null, 0.5, 0.0, 1.0);
        System.out.println("[DEBUG_LOG] Color for value=0.5, min=0.0, max=1.0: " + colorToString(color2));
        assertNotEquals(Color.WHITE, color2, "Color should not be white for middle value");

        Color color3 = (Color) getColorForValueMethod.invoke(null, 1.0, 0.0, 1.0);
        System.out.println("[DEBUG_LOG] Color for value=1.0, min=0.0, max=1.0: " + colorToString(color3));
        assertNotEquals(Color.WHITE, color3, "Color should not be white for maximum value");
    }

    /**
     * Test that the applyHillShading method correctly applies illumination to colors.
     * This test uses reflection to access the private method.
     */
    @Test
    public void testApplyHillShading() throws Exception {
        // Get the private method using reflection
        Method applyHillShadingMethod = GridLayer.class.getDeclaredMethod(
                "applyHillShading", Color.class, double.class, double.class);
        applyHillShadingMethod.setAccessible(true);

        // Test with different illumination and intensity values
        Color baseColor = new Color(0.5f, 0.5f, 0.5f, 1.0f); // Gray

        Color shadedColor1 = (Color) applyHillShadingMethod.invoke(null, baseColor, 1.0, 0.5);
        System.out.println("[DEBUG_LOG] Shaded color with illumination=1.0, intensity=0.5: " + colorToString(shadedColor1));
        // With illumination=1.0, the color should remain approximately the same (allowing for small rounding differences)
        assertTrue(Math.abs(baseColor.getRed() - shadedColor1.getRed()) <= 1, "Red component should be approximately unchanged with illumination=1.0");
        assertTrue(Math.abs(baseColor.getGreen() - shadedColor1.getGreen()) <= 1, "Green component should be approximately unchanged with illumination=1.0");
        assertTrue(Math.abs(baseColor.getBlue() - shadedColor1.getBlue()) <= 1, "Blue component should be approximately unchanged with illumination=1.0");

        Color shadedColor2 = (Color) applyHillShadingMethod.invoke(null, baseColor, 0.5, 1.0);
        System.out.println("[DEBUG_LOG] Shaded color with illumination=0.5, intensity=1.0: " + colorToString(shadedColor2));
        assertTrue(shadedColor2.getRed() < baseColor.getRed(), "Red component should be darker with illumination=0.5");
        assertTrue(shadedColor2.getGreen() < baseColor.getGreen(), "Green component should be darker with illumination=0.5");
        assertTrue(shadedColor2.getBlue() < baseColor.getBlue(), "Blue component should be darker with illumination=0.5");

        Color shadedColor3 = (Color) applyHillShadingMethod.invoke(null, baseColor, 0.0, 1.0);
        System.out.println("[DEBUG_LOG] Shaded color with illumination=0.0, intensity=1.0: " + colorToString(shadedColor3));
        assertEquals(0, shadedColor3.getRed(), "Red component should be 0 with illumination=0.0");
        assertEquals(0, shadedColor3.getGreen(), "Green component should be 0 with illumination=0.0");
        assertEquals(0, shadedColor3.getBlue(), "Blue component should be 0 with illumination=0.0");
    }

    /**
     * Test the full hill-shading process to ensure it doesn't produce white images.
     * This test simulates the process used in the print method.
     */
    @Test
    public void testFullHillShadingProcess() throws Exception {
        // Get the private methods using reflection
        Method getColorForValueMethod = GridLayer.class.getDeclaredMethod(
                "getColorForValue", double.class, double.class, double.class);
        getColorForValueMethod.setAccessible(true);

        Method calculateHillShadingMethod = GridLayer.class.getDeclaredMethod(
                "calculateHillShading", float[][].class, int.class, int.class, double.class, double.class);
        calculateHillShadingMethod.setAccessible(true);

        Method applyHillShadingMethod = GridLayer.class.getDeclaredMethod(
                "applyHillShading", Color.class, double.class, double.class);
        applyHillShadingMethod.setAccessible(true);

        // Create a simple grid with a slope
        float[][] gridData = new float[3][3];
        gridData[0][0] = 0.0f;
        gridData[0][1] = 0.0f;
        gridData[0][2] = 0.0f;
        gridData[1][0] = 0.5f;
        gridData[1][1] = 0.5f;
        gridData[1][2] = 0.5f;
        gridData[2][0] = 1.0f;
        gridData[2][1] = 1.0f;
        gridData[2][2] = 1.0f;

        // Simulate the process used in the print method
        float minValue = 0.0f;
        float maxValue = 1.0f;
        double hillShadingAzimuth = 315.0;
        double hillShadingAltitude = 45.0;
        double hillShadingIntensity = 0.5;

        // Check that none of the cells become white after hill-shading
        for (int i = 0; i < gridData.length; i++) {
            for (int j = 0; j < gridData[0].length; j++) {
                float value = gridData[i][j];

                // Get base color for the value
                Color baseColor = (Color) getColorForValueMethod.invoke(null, value, minValue, maxValue);

                // Calculate illumination for this cell
                double illumination = (double) calculateHillShadingMethod.invoke(
                    null, gridData, i, j, hillShadingAzimuth, hillShadingAltitude);

                // Apply hill-shading to the color
                Color shadedColor = (Color) applyHillShadingMethod.invoke(
                    null, baseColor, illumination, hillShadingIntensity);

                System.out.println("[DEBUG_LOG] Cell [" + i + "," + j + "] value=" + value + 
                    ", baseColor=" + colorToString(baseColor) + 
                    ", illumination=" + illumination + 
                    ", shadedColor=" + colorToString(shadedColor));

                assertNotEquals(Color.WHITE, shadedColor, "Shaded color should not be white");

                // Check if cells with different values have different colors
                // Only compare cells that have different values
                if (value != gridData[0][0]) {
                    Color prevShadedColor = (Color) applyHillShadingMethod.invoke(
                        null, (Color) getColorForValueMethod.invoke(null, gridData[0][0], minValue, maxValue),
                        (double) calculateHillShadingMethod.invoke(null, gridData, 0, 0, hillShadingAzimuth, hillShadingAltitude),
                        hillShadingIntensity);

                    // At least one component should be different for different values
                    boolean allSame = shadedColor.getRed() == prevShadedColor.getRed() &&
                                     shadedColor.getGreen() == prevShadedColor.getGreen() &&
                                     shadedColor.getBlue() == prevShadedColor.getBlue();

                    assertFalse(allSame, "Cells with different values should have different colors");
                }
            }
        }
    }

    private String colorToString(Color color) {
        return "Color[r=" + color.getRed() + ",g=" + color.getGreen() + ",b=" + color.getBlue() + ",a=" + color.getAlpha() + "]";
    }
}
