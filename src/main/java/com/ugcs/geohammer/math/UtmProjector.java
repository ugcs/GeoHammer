package com.ugcs.geohammer.math;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

public class UtmProjector {

    private static final CRSFactory CRS_FACTORY = new CRSFactory();

    private static final CoordinateTransformFactory TRANSFORM_FACTORY = new CoordinateTransformFactory();

    private final CoordinateTransform transform;

    private final int zone;

    private final boolean north;

    // use proj4 parameter strings to avoid dependency on EPSG data files

    public static CoordinateReferenceSystem utm(int zone, boolean north) {
        String utmParams = String.format("+proj=utm +zone=%d +datum=WGS84 +units=m +no_defs%s",
                zone, north ? "" : " +south");
        return CRS_FACTORY.createFromParameters("UTM" + zone + (north ? "N" : "S"), utmParams);
    }

    public static CoordinateReferenceSystem wgs84() {
        String wgs84Params = "+proj=longlat +datum=WGS84 +no_defs";
        return CRS_FACTORY.createFromParameters("WGS84", wgs84Params);
    }

    public static CoordinateReferenceSystem mercator() {
        String mercatorParams = "+proj=merc +a=6378137 +b=6378137 +units=m +no_defs";
        return CRS_FACTORY.createFromParameters("SphericalMercator", mercatorParams);
    }

    public UtmProjector(double centerLatitude, double centerLongitude) {
        this(centerLatitude, centerLongitude, wgs84());
    }

    public UtmProjector(double centerLatitude, double centerLongitude, CoordinateReferenceSystem sourceCrs) {
        this.zone = calculateZone(centerLatitude, centerLongitude);
        this.north = centerLatitude >= 0;
        CoordinateReferenceSystem targetCrs = utm(zone, north);
        this.transform = TRANSFORM_FACTORY.createTransform(sourceCrs, targetCrs);
    }

    /**
     * Calculates the UTM zone for a given latitude/longitude, accounting for
     * the Norway (zone V, 56°–64°N) and Svalbard (zone X, 72°–84°N) exceptions
     * where standard 6° zone boundaries are modified.
     */
    private static int calculateZone(double latitude, double longitude) {
        int zone = (int) Math.floor((longitude + 180.0) / 6.0) + 1;

        // Norway exception: zone band V (56°N–64°N), lon 3°–6°E belongs to zone 32, not 31
        if (latitude >= 56.0 && latitude < 64.0 && longitude >= 3.0 && longitude < 6.0) {
            return 32;
        }

        // Svalbard exception: zone band X (72°N–84°N), even zones 32/34/36 don't exist
        if (latitude >= 72.0 && latitude < 84.0) {
            if (longitude >= 0.0 && longitude < 9.0) {
                return 31;
            }
            if (longitude >= 9.0 && longitude < 21.0) {
                return 33;
            }
            if (longitude >= 21.0 && longitude < 33.0) {
                return 35;
            }
            if (longitude >= 33.0 && longitude < 42.0) {
                return 37;
            }
        }

        return zone;
    }

    // project to UTM (easting, northing)
    public ProjCoordinate project(double x, double y) {
        ProjCoordinate source = new ProjCoordinate(x, y);
        ProjCoordinate target = new ProjCoordinate();
        return transform.transform(source, target);
    }

    public ProjCoordinate project(ProjCoordinate source, ProjCoordinate target) {
        return transform.transform(source, target);
    }

    public int zone() {
        return zone;
    }

    public String zoneName() {
        return String.format("UTM %d%s", zone, north ? "N" : "S");
    }

    public String coordinateSystemName() {
        return String.format("WGS 84 / UTM zone %d%s", zone, north ? "N" : "S");
    }

    public int epsgCode() {
        return (north ? 32600 : 32700) + zone;
    }
}