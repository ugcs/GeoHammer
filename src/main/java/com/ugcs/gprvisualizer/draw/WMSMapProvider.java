package com.ugcs.gprvisualizer.draw;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import org.jspecify.annotations.Nullable;

public class WMSMapProvider implements MapProvider {

    private static final int IMAGE_SIZE = 1200;
    private static final String DEFAULT_FORMAT = "image/png";
    private static final String DEFAULT_CRS = "EPSG:4326";
    private static final String WMS_VERSION = "1.3.0";
    
    private final String serverUrl;
    private final String layers;
    private final String format;

    public WMSMapProvider(String serverUrl, String layers, String format) {
        this.serverUrl = serverUrl != null ? serverUrl.trim() : "";
        this.layers = layers != null ? layers.trim() : "";
        this.format = format != null ? format.trim() : DEFAULT_FORMAT;
    }

    @Override
    public int getMaxZoom() {
        return 18; // Reasonable default for WMS servers
    }

    @Override
    public int getMinZoom() {
        return 1;
    }

    @Nullable
    @Override
    public BufferedImage loadimg(MapField field) {
        if (serverUrl.isEmpty() || layers.isEmpty()) {
            System.err.println("WMS server URL or layers not configured");
            return null;
        }

        // Validate zoom level
        if (field.getZoom() > getMaxZoom()) {
            field.setZoom(getMaxZoom());
        }
        if (field.getZoom() < getMinZoom()) {
            field.setZoom(getMinZoom());
        }

        LatLon center = field.getSceneCenter();
        if (center == null) {
            System.err.println("Scene center is null");
            return null;
        }

        try {
            // Calculate bounding box around the center point
            // The size of the bbox depends on the zoom level
            double bboxSize = calculateBboxSize(field.getZoom());
            double minLat = center.getLatDgr() - bboxSize / 2;
            double maxLat = center.getLatDgr() + bboxSize / 2;
            double minLon = center.getLonDgr() - bboxSize / 2;
            double maxLon = center.getLonDgr() + bboxSize / 2;

            String wmsUrl = buildWMSUrl(minLat, minLon, maxLat, maxLon);
            System.out.println("WMS URL: " + wmsUrl);

            System.setProperty("java.net.useSystemProxies", "true");
            return ImageIO.read(new URI(wmsUrl).toURL());

        } catch (IOException | URISyntaxException e) {
            System.err.println("Error loading WMS image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String buildWMSUrl(double minLat, double minLon, double maxLat, double maxLon) {
        DecimalFormat df = new DecimalFormat("#.0000000", DecimalFormatSymbols.getInstance(Locale.US));
        
        StringBuilder url = new StringBuilder(serverUrl);
        
        // Add ? or & depending on whether URL already has parameters
        char separator = serverUrl.contains("?") ? '&' : '?';
        
        try {
            url.append(separator).append("SERVICE=WMS");
            url.append("&VERSION=").append(WMS_VERSION);
            url.append("&REQUEST=GetMap");
            url.append("&LAYERS=").append(URLEncoder.encode(layers, StandardCharsets.UTF_8));
            url.append("&STYLES=");
            url.append("&CRS=").append(DEFAULT_CRS);
            
            // For EPSG:4326 in WMS 1.3.0, axis order is lat,lon (north,east)
            url.append("&BBOX=").append(df.format(minLat))
               .append(",").append(df.format(minLon))
               .append(",").append(df.format(maxLat))
               .append(",").append(df.format(maxLon));
               
            url.append("&WIDTH=").append(IMAGE_SIZE);
            url.append("&HEIGHT=").append(IMAGE_SIZE);
            url.append("&FORMAT=").append(URLEncoder.encode(format, StandardCharsets.UTF_8));
            url.append("&TRANSPARENT=TRUE");
            
        } catch (Exception e) {
            System.err.println("Error building WMS URL: " + e.getMessage());
        }
        
        return url.toString();
    }

    private double calculateBboxSize(int zoom) {
        // Calculate the geographic size of the bounding box based on zoom level
        // This is similar to how Google Maps calculates tile extents
        // At zoom 1, we want to show a large area, at zoom 18, a small area
        
        // Base size at zoom 1 (approximately half the world)
        double baseSize = 180.0;
        
        // Each zoom level halves the size
        return baseSize / Math.pow(2, zoom - 1);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getLayers() {
        return layers;
    }

    public String getFormat() {
        return format;
    }
}