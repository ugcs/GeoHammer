package com.ugcs.geohammer.service.magnetics;

import com.ugcs.geohammer.util.Check;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class IgrfModel {

    private static final int MAX_DEGREE = 13;

    private static final double REFERENCE_RADIUS_KILOMETERS = 6371.2;

    private static final double EQUATORIAL_RADIUS_KILOMETERS = 6378.137;

    private static final double POLAR_RADIUS_KILOMETERS = 6356.7523142;

    private static final double FIRST_EPOCH = 1900;

    private static final double LAST_EPOCH = 2025;

    private static final double LAST_VALID_YEAR = 2030;

    private final List<Coefficient> coefficients;

    public IgrfModel() {
        coefficients = loadCoefficients();
    }

    public IgrfField evaluate(Instant timestamp, double latitudeDegrees, double longitudeDegrees,
                              double altitudeMeters) {
        Check.notNull(timestamp);
        Check.condition(Double.isFinite(latitudeDegrees) && Math.abs(latitudeDegrees) <= 90,
                "Latitude must be between -90 and 90 degrees");
        Check.condition(Double.isFinite(longitudeDegrees), "Longitude must be finite");
        Check.condition(Double.isFinite(altitudeMeters), "Altitude must be finite");

        double decimalYear = decimalYear(timestamp);
        Check.condition(decimalYear >= FIRST_EPOCH && decimalYear <= LAST_VALID_YEAR,
                "IGRF-14 supports dates from 1900 through 2030");

        double[][] g = new double[MAX_DEGREE + 1][MAX_DEGREE + 1];
        double[][] h = new double[MAX_DEGREE + 1][MAX_DEGREE + 1];
        for (Coefficient coefficient : coefficients) {
            double value = coefficient.valueAt(decimalYear);
            if (coefficient.cosine()) {
                g[coefficient.degree()][coefficient.order()] = value;
            } else {
                h[coefficient.degree()][coefficient.order()] = value;
            }
        }

        return synthesize(g, h, latitudeDegrees, longitudeDegrees, altitudeMeters / 1000);
    }

    private static IgrfField synthesize(double[][] g, double[][] h, double latitudeDegrees,
                                         double longitudeDegrees, double altitudeKilometers) {
        double latitude = Math.toRadians(latitudeDegrees);
        double longitude = Math.toRadians(longitudeDegrees);
        double sinLatitude = Math.sin(latitude);
        double cosLatitude = Math.cos(latitude);

        double a2 = EQUATORIAL_RADIUS_KILOMETERS * EQUATORIAL_RADIUS_KILOMETERS;
        double b2 = POLAR_RADIUS_KILOMETERS * POLAR_RADIUS_KILOMETERS;
        double c2 = a2 - b2;
        double q = Math.sqrt(a2 - c2 * sinLatitude * sinLatitude);
        double q1 = altitudeKilometers * q;
        double q2 = Math.pow((q1 + a2) / (q1 + b2), 2);
        double cosTheta = sinLatitude / Math.sqrt(q2);
        double sinTheta = Math.sqrt(1 - cosTheta * cosTheta);
        double r2 = altitudeKilometers * altitudeKilometers + 2 * q1
                + (a2 * a2 - (a2 * a2 - b2 * b2) * sinLatitude * sinLatitude) / (q * q);
        double radius = Math.sqrt(r2);
        double d = Math.sqrt(a2 * cosLatitude * cosLatitude + b2 * sinLatitude * sinLatitude);
        double rotationCos = (altitudeKilometers + d) / radius;
        double rotationSin = c2 * cosLatitude * sinLatitude / (radius * d);

        double[] cosLongitude = new double[MAX_DEGREE + 1];
        double[] sinLongitude = new double[MAX_DEGREE + 1];
        cosLongitude[0] = 1;
        sinLongitude[0] = 0;
        cosLongitude[1] = Math.cos(longitude);
        sinLongitude[1] = Math.sin(longitude);
        for (int order = 2; order <= MAX_DEGREE; order++) {
            cosLongitude[order] = cosLongitude[1] * cosLongitude[order - 1]
                    - sinLongitude[1] * sinLongitude[order - 1];
            sinLongitude[order] = sinLongitude[1] * cosLongitude[order - 1]
                    + cosLongitude[1] * sinLongitude[order - 1];
        }

        double[][] normalization = schmidtNormalization();
        double[][] p = new double[MAX_DEGREE + 1][MAX_DEGREE + 1];
        double[][] dp = new double[MAX_DEGREE + 1][MAX_DEGREE + 1];
        p[0][0] = 1;
        double northSpherical = 0;
        double east = 0;
        double downSpherical = 0;
        for (int degree = 1; degree <= MAX_DEGREE; degree++) {
            double radiusPower = Math.pow(REFERENCE_RADIUS_KILOMETERS / radius, degree + 2);
            for (int order = 0; order <= degree; order++) {
                updateLegendre(p, dp, degree, order, sinTheta, cosTheta);
                double cosineCoefficient = g[degree][order] * normalization[degree][order];
                double sineCoefficient = h[degree][order] * normalization[degree][order];
                double longitudinalField = cosineCoefficient * cosLongitude[order]
                        + sineCoefficient * sinLongitude[order];
                double eastField = cosineCoefficient * sinLongitude[order]
                        - sineCoefficient * cosLongitude[order];
                northSpherical += radiusPower * longitudinalField * dp[degree][order];
                downSpherical -= radiusPower * (degree + 1) * longitudinalField * p[degree][order];
                if (order > 0 && sinTheta > 1e-10) {
                    east += radiusPower * order * eastField * p[degree][order] / sinTheta;
                }
            }
        }

        double north = northSpherical * rotationCos + downSpherical * rotationSin;
        double down = -northSpherical * rotationSin + downSpherical * rotationCos;
        return new IgrfField(Math.sqrt(north * north + east * east + down * down), north, -east, down);
    }

    private static void updateLegendre(double[][] p, double[][] dp, int degree, int order,
                                       double sinTheta, double cosTheta) {
        if (degree == order) {
            p[degree][order] = sinTheta * p[degree - 1][order - 1];
            dp[degree][order] = sinTheta * dp[degree - 1][order - 1]
                    + cosTheta * p[degree - 1][order - 1];
        } else if (degree == 1) {
            p[degree][order] = cosTheta * p[degree - 1][order];
            dp[degree][order] = cosTheta * dp[degree - 1][order] - sinTheta * p[degree - 1][order];
        } else {
            double k = ((degree - 1.0) * (degree - 1.0) - order * order)
                    / ((2.0 * degree - 1) * (2.0 * degree - 3));
            p[degree][order] = cosTheta * p[degree - 1][order] - k * p[degree - 2][order];
            dp[degree][order] = cosTheta * dp[degree - 1][order] - sinTheta * p[degree - 1][order]
                    - k * dp[degree - 2][order];
        }
    }

    private static double[][] schmidtNormalization() {
        double[][] normalization = new double[MAX_DEGREE + 1][MAX_DEGREE + 1];
        normalization[0][0] = 1;
        for (int degree = 1; degree <= MAX_DEGREE; degree++) {
            normalization[degree][0] = normalization[degree - 1][0] * (2.0 * degree - 1) / degree;
            for (int order = 1; order <= degree; order++) {
                double multiplier = (degree - order + 1.0) * (order == 1 ? 2 : 1) / (degree + order);
                normalization[degree][order] = normalization[degree][order - 1] * Math.sqrt(multiplier);
            }
        }
        return normalization;
    }

    private static double decimalYear(Instant instant) {
        ZonedDateTime dateTime = instant.atZone(ZoneOffset.UTC);
        int year = dateTime.getYear();
        int days = dateTime.toLocalDate().lengthOfYear();
        return year + (dateTime.getDayOfYear() - 1
                + (dateTime.getHour() * 3600 + dateTime.getMinute() * 60 + dateTime.getSecond()) / 86400.0) / days;
    }

    private static List<Coefficient> loadCoefficients() {
        InputStream stream = IgrfModel.class.getResourceAsStream("/igrf14coeffs.txt");
        if (stream == null) {
            throw new IllegalStateException("IGRF-14 coefficients are missing");
        }
        List<Coefficient> values = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank() || line.startsWith("c/s")) {
                    continue;
                }
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 30 || (!fields[0].equals("g") && !fields[0].equals("h"))) {
                    continue;
                }
                boolean cosine = fields[0].equals("g");
                int degree = Integer.parseInt(fields[1]);
                int order = Integer.parseInt(fields[2]);
                double[] epochs = new double[26];
                for (int i = 0; i < epochs.length; i++) {
                    epochs[i] = Double.parseDouble(fields[i + 3]);
                }
                double secularVariation = Double.parseDouble(fields[29]);
                values.add(new Coefficient(cosine, degree, order, epochs, secularVariation));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read IGRF-14 coefficients", e);
        }
        Check.condition(!values.isEmpty(), "IGRF-14 coefficients are empty");
        return List.copyOf(values);
    }

    private record Coefficient(boolean cosine, int degree, int order, double[] epochs, double secularVariation) {

        private double valueAt(double year) {
            if (year >= LAST_EPOCH) {
                return epochs[25] + (year - LAST_EPOCH) * secularVariation;
            }
            int index = (int) Math.floor((year - FIRST_EPOCH) / 5);
            double fraction = (year - (FIRST_EPOCH + index * 5)) / 5;
            return epochs[index] + fraction * (epochs[index + 1] - epochs[index]);
        }
    }
}
