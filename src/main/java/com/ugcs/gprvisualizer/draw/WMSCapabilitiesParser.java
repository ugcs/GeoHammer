package com.ugcs.gprvisualizer.draw;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WMSCapabilitiesParser {
    
    public static class LayerInfo {
        private final String name;
        private final String title;
        private final String abstractText;
        
        public LayerInfo(String name, String title, String abstractText) {
            this.name = name;
            this.title = title != null ? title : name;
            this.abstractText = abstractText;
        }
        
        public String getName() {
            return name;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getAbstractText() {
            return abstractText;
        }
        
        @Override
        public String toString() {
            return title;
        }
    }
    
    public static List<LayerInfo> fetchAndParseLayers(String baseUrl) throws IOException {
        String capabilitiesUrl = buildCapabilitiesUrl(baseUrl);
        String xmlResponse = fetchCapabilities(capabilitiesUrl);
        return parseLayersFromXml(xmlResponse);
    }
    
    private static String buildCapabilitiesUrl(String baseUrl) {
        try {
            StringBuilder url = new StringBuilder(baseUrl);
            if (!baseUrl.contains("?")) {
                url.append("?");
            } else if (!baseUrl.endsWith("&")) {
                url.append("&");
            }
            
            url.append("SERVICE=").append(URLEncoder.encode("WMS", StandardCharsets.UTF_8));
            url.append("&VERSION=").append(URLEncoder.encode("1.3.0", StandardCharsets.UTF_8));
            url.append("&REQUEST=").append(URLEncoder.encode("GetCapabilities", StandardCharsets.UTF_8));
            
            return url.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build capabilities URL", e);
        }
    }
    
    private static String fetchCapabilities(String capabilitiesUrl) throws IOException {
        URL url = new URL(capabilitiesUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "GeoHammer/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            
            try (InputStream inputStream = connection.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
    
    public static List<LayerInfo> parseLayersFromXml(String xmlContent) {
        List<LayerInfo> layers = new ArrayList<>();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
            
            NodeList layerNodes = document.getElementsByTagName("Layer");
            
            for (int i = 0; i < layerNodes.getLength(); i++) {
                Element layerElement = (Element) layerNodes.item(i);
                
                String name = getElementText(layerElement, "Name");
                if (name != null && !name.trim().isEmpty()) {
                    String title = getElementText(layerElement, "Title");
                    String abstractText = getElementText(layerElement, "Abstract");
                    
                    layers.add(new LayerInfo(name.trim(), title, abstractText));
                }
            }
            
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException("Failed to parse WMS capabilities", e);
        }
        
        return layers;
    }
    
    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
}