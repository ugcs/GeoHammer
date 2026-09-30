package com.ugcs.geohammer.map.layer;

import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.service.palette.HueGradient;
import com.ugcs.geohammer.service.palette.LinearPalette;
import com.ugcs.geohammer.service.palette.Palette;
import com.ugcs.geohammer.service.palette.Spectrum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;

public class HillShadingTest {

    @Test
    public void testComputeIllumination() {
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
        double illumination1 = HillShading.computeIllumination(gridData, 1, 1, 1.0);
        System.out.println("[DEBUG_LOG] Illumination: " + illumination1);
        assertTrue(illumination1 >= 0.0 && illumination1 <= 1.0, "Illumination should be in range [0, 1]");

        // Test edge case: on a plane, edge cells are shaded as the interior ones
        double illuminationEdge = HillShading.computeIllumination(gridData, 0, 0, 1.0);
        System.out.println("[DEBUG_LOG] Illumination at edge: " + illuminationEdge);
        assertEquals(illumination1, illuminationEdge, 1e-9, "Edge cells should be shaded as interior cells on a plane");
    }

    @Test
    public void testGetColorForValue() {
        Spectrum spectrum = new HueGradient();
        Palette palette = new LinearPalette(spectrum, new Range(0.0, 1.0));

        Color color1 = palette.getColor(0.0);
        System.out.println("[DEBUG_LOG] Color for value=0.0, min=0.0, max=1.0: " + colorToString(color1));
        assertNotEquals(Color.WHITE, color1, "Color should not be white for minimum value");

        Color color2 = palette.getColor(0.5);
        System.out.println("[DEBUG_LOG] Color for value=0.5, min=0.0, max=1.0: " + colorToString(color2));
        assertNotEquals(Color.WHITE, color2, "Color should not be white for middle value");

        Color color3 = palette.getColor(1.0);
        System.out.println("[DEBUG_LOG] Color for value=1.0, min=0.0, max=1.0: " + colorToString(color3));
        assertNotEquals(Color.WHITE, color3, "Color should not be white for maximum value");
    }

    @Test
    public void testApply() {
        // Test with different illumination and intensity values
        Color baseColor = new Color(0.5f, 0.5f, 0.5f, 1.0f); // Gray

        Color shadedColor1 = HillShading.apply(baseColor, 1.0);
        System.out.println("[DEBUG_LOG] Shaded color with illumination=1.0, intensity=0.5: " + colorToString(shadedColor1));
        // With illumination=1.0, the color should remain approximately the same (allowing for small rounding differences)
        assertTrue(Math.abs(baseColor.getRed() - shadedColor1.getRed()) <= 1, "Red component should be approximately unchanged with illumination=1.0");
        assertTrue(Math.abs(baseColor.getGreen() - shadedColor1.getGreen()) <= 1, "Green component should be approximately unchanged with illumination=1.0");
        assertTrue(Math.abs(baseColor.getBlue() - shadedColor1.getBlue()) <= 1, "Blue component should be approximately unchanged with illumination=1.0");

        Color shadedColor2 = HillShading.apply(baseColor, 0.5);
        System.out.println("[DEBUG_LOG] Shaded color with illumination=0.5, intensity=1.0: " + colorToString(shadedColor2));
        assertTrue(shadedColor2.getRed() < baseColor.getRed(), "Red component should be darker with illumination=0.5");
        assertTrue(shadedColor2.getGreen() < baseColor.getGreen(), "Green component should be darker with illumination=0.5");
        assertTrue(shadedColor2.getBlue() < baseColor.getBlue(), "Blue component should be darker with illumination=0.5");
    }

    @Test
    public void testFullHillShadingProcess() {
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
        Spectrum spectrum = new HueGradient();
        Palette palette = new LinearPalette(spectrum, new Range(minValue, maxValue));
        double zScale = 1.0;

        // Check that none of the cells become white after hill-shading
        for (int i = 0; i < gridData.length; i++) {
            for (int j = 0; j < gridData[0].length; j++) {
                float value = gridData[i][j];

                // Get base color for the value
                Color baseColor = palette.getColor(value);

                // Calculate illumination for this cell
                double illumination = HillShading.computeIllumination(gridData, i, j, zScale);

                // Apply hill-shading to the color
                Color shadedColor = HillShading.apply(baseColor, illumination);

                System.out.println("[DEBUG_LOG] Cell [" + i + "," + j + "] value=" + value +
                    ", baseColor=" + colorToString(baseColor) +
                    ", illumination=" + illumination +
                    ", shadedColor=" + colorToString(shadedColor));

                assertNotEquals(Color.WHITE, shadedColor, "Shaded color should not be white");

                // Check if cells with different values have different colors
                // Only compare cells that have different values
                if (value != gridData[0][0]) {
                    Color prevBaseColor = palette.getColor(gridData[0][0]);
                    Color prevShadedColor = HillShading.apply(
                        prevBaseColor,
                        HillShading.computeIllumination(gridData, 0, 0, zScale));

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
