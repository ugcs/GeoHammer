package com.ugcs.gprvisualizer.app.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.draw.GoogleMapProvider;

import java.awt.image.BufferedImage;

public class GoogleMapProviderTest {

    @Test
    public void testLoading() {
        // Create a mock MapField object
        MapField field = new MapField() {
            @Override
            public boolean isActive() {
                return true;
            }
        };
        
        field.setZoom(10);
        field.setSceneCenter(new LatLon(52.520008, 13.404954)); // Berlin coordinates

        // Create an instance of GoogleMapProvider
        GoogleMapProvider mapProvider = new GoogleMapProvider();

        // Call the loadimg method
        BufferedImage image = mapProvider.loading(field);

        // Assert that the returned image is not null
        Assertions.assertNotNull(image);
    }

    @Test
    public void testGetMaxZoom() {
        // Create an instance of GoogleMapProvider
        GoogleMapProvider mapProvider = new GoogleMapProvider();

        // Call the getMaxZoom method
        int maxZoom = mapProvider.getMaxZoom();

        // Assert that the returned maxZoom is equal to 20
        Assertions.assertEquals(20, maxZoom);
    }

    // Add more tests for other methods if needed

}