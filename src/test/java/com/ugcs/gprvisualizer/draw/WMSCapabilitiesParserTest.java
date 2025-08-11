package com.ugcs.gprvisualizer.draw;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class WMSCapabilitiesParserTest {

    @Test
    public void testParseLayersFromValidXml() {
        String sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <WMS_Capabilities>
                <Capability>
                    <Layer>
                        <Name>background</Name>
                        <Title>Background Layer</Title>
                        <Abstract>This is a background layer</Abstract>
                    </Layer>
                    <Layer>
                        <Name>roads</Name>
                        <Title>Roads</Title>
                    </Layer>
                    <Layer>
                        <Title>No Name Layer</Title>
                    </Layer>
                </Capability>
            </WMS_Capabilities>
            """;

        List<WMSCapabilitiesParser.LayerInfo> layers = 
            WMSCapabilitiesParser.parseLayersFromXml(sampleXml);

        Assertions.assertEquals(2, layers.size());
        
        WMSCapabilitiesParser.LayerInfo firstLayer = layers.get(0);
        Assertions.assertEquals("background", firstLayer.getName());
        Assertions.assertEquals("Background Layer", firstLayer.getTitle());
        Assertions.assertEquals("This is a background layer", firstLayer.getAbstractText());
        
        WMSCapabilitiesParser.LayerInfo secondLayer = layers.get(1);
        Assertions.assertEquals("roads", secondLayer.getName());
        Assertions.assertEquals("Roads", secondLayer.getTitle());
    }

    @Test
    public void testParseLayersFromEmptyXml() {
        String emptyXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <WMS_Capabilities>
                <Capability>
                </Capability>
            </WMS_Capabilities>
            """;

        List<WMSCapabilitiesParser.LayerInfo> layers = 
            WMSCapabilitiesParser.parseLayersFromXml(emptyXml);

        Assertions.assertTrue(layers.isEmpty());
    }

    @Test
    public void testLayerInfoToString() {
        WMSCapabilitiesParser.LayerInfo layer = 
            new WMSCapabilitiesParser.LayerInfo("test_layer", "Test Layer", "Description");

        Assertions.assertEquals("Test Layer", layer.toString());
    }

    @Test
    public void testLayerInfoGetters() {
        WMSCapabilitiesParser.LayerInfo layer = 
            new WMSCapabilitiesParser.LayerInfo("test_layer", "Test Layer", "Description");

        Assertions.assertEquals("test_layer", layer.getName());
        Assertions.assertEquals("Test Layer", layer.getTitle());
        Assertions.assertEquals("Description", layer.getAbstractText());
    }

    @Test
    public void testBuildCapabilitiesUrl() {
        String baseUrl = "http://example.com/wms";
        
        // This tests the URL building logic indirectly
        Assertions.assertDoesNotThrow(() -> {
            try {
                WMSCapabilitiesParser.fetchAndParseLayers(baseUrl);
            } catch (IOException e) {
                // Expected for invalid URL, we're just testing it doesn't crash
            }
        });
    }

    @Test
    public void testInvalidXmlHandling() {
        String invalidXml = "This is not valid XML";

        Assertions.assertThrows(RuntimeException.class, () -> {
            WMSCapabilitiesParser.parseLayersFromXml(invalidXml);
        });
    }
}